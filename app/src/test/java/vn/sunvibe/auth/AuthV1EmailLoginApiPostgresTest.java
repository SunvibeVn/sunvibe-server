package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Sign-in by email and password on PostgreSQL, with the phone channel off (auth-email-access S-14, S-19, S-11, 5.5):
 * every refusal looks the same and costs the same hash, and an unverified address touches no counter.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"app.auth.phone.mode=off",
			"app.sms.provider=none" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class, AuthV1EmailLoginApiPostgresTest.CountingEncoder.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV1EmailLoginApiPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String SEND = "/api/v1/auth/registration/email/send-code";
	private static final AtomicInteger MATCHES = new AtomicInteger();

	/** Counts every password check, the one Spring runs against a dummy hash for an unknown user included. */
	@TestConfiguration(proxyBeanMethods = false)
	static class CountingEncoder {
		@Bean
		@Primary
		BCryptPasswordEncoder countingPasswordEncoder() {
			return new BCryptPasswordEncoder(4) {
				@Override
				protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
					MATCHES.incrementAndGet();
					return super.matchesNonNull(rawPassword, encodedPassword);
				}
			};
		}
	}

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private CapturingMessagesConfig messages;

	@Autowired
	private JdbcTemplate jdbc;

	private AuthV1ApiTestSupport api;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
	}

	@AfterAll
	void tearDown() {
		api.close();
	}

	private HttpResponse<String> login(String email, String password) throws Exception {
		return api.post(LOGIN, null, null, null, api.json().writeValueAsString(Map.of("email", email, "password", password)));
	}

	/** BR-7: a password the DTO refuses is refused before any lookup, so it says nothing about the account. */
	@Test
	void login_emptyOrOverlongPassword_same400_notCounted() throws Exception {
		Created known = api.createEmailUser("CUSTOMER");

		for (String password : List.of("", "x".repeat(300))) {
			HttpResponse<String> forKnown = login(known.username(), password);
			HttpResponse<String> forUnknown = login(freshAddress(), password);

			assertEquals(400, forKnown.statusCode(), "status " + forKnown.statusCode());
			assertEquals("VALIDATION_FAILED", api.body(forKnown).path("code").asString());
			assertEquals(api.body(forKnown).path("errors").toString(), api.body(forUnknown).path("errors").toString());
		}
		assertEquals(0, jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, known.userId()));
	}

	private static String freshAddress() {
		return "login-" + UUID.randomUUID() + "@example.com";
	}

	/** A row as a test wants it: its password is the id of the row. */
	private String row(String email, String emailKey, boolean verified, String status) {
		String id = ulidGenerator.generateId();
		Timestamp now = Timestamp.from(AppClock.currentUTC());
		jdbc.update("INSERT INTO users (id, full_name, email, email_key, email_verified_at, password, kyc_status,"
				+ " user_status, created_date, last_update) VALUES (?, 'Login row', ?, ?, ?, ?, 'UNVERIFIED', ?, ?, ?)", id,
				email, emailKey, verified ? now : null, passwordEncoder.encode(id), status, now, now);
		return id;
	}

	private int failedAttempts(String userId) {
		return jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, userId);
	}

	@Test
	void login_caseAndEdgeSpaces_sameAccount() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");

		for (String typed : List.of(user.username(), user.username().toUpperCase(), "  " + user.username() + " ")) {
			HttpResponse<String> response = login(typed, user.password());

			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			assertEquals(user.userId(), api.body(response).path("user").path("userId").asString());
		}
	}

	@Test
	void login_noOtpRequired() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");

		HttpResponse<String> response = login(user.username(), user.password());

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		assertFalse(api.body(response).path("accessToken").asString().isBlank());
		assertFalse(messages.hasMessage(user.username()), "no code goes out");
	}

	/** Every way of being refused has one body, so the answer never tells which one it was. */
	@Test
	void login_unknownUnverifiedKeylessWrongPassword_sameBody() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		String unverified = freshAddress();
		String keyless = freshAddress();
		String locked = freshAddress();
		String terminated = freshAddress();
		String deleted = freshAddress();
		String unverifiedPassword = row(unverified, unverified, false, "ACTIVE");
		String keylessPassword = row(keyless, null, true, "ACTIVE");
		String lockedPassword = row(locked, locked, true, "LOCK");
		String terminatedPassword = row(terminated, terminated, true, "TERMINATED");
		String deletedPassword = row(deleted, deleted, true, "ACTIVE");
		jdbc.update("UPDATE users SET deleted_at = ? WHERE id = ?", Timestamp.from(AppClock.currentUTC()), deletedPassword);

		List<HttpResponse<String>> refusals = List.of(login(freshAddress(), "whatever"),
				login(unverified, unverifiedPassword), login(keyless, keylessPassword), login(locked, lockedPassword),
				login(terminated, terminatedPassword), login(deleted, deletedPassword), login(user.username(), "wrong"));

		JsonNode first = api.body(refusals.getFirst());
		assertEquals("INVALID_CREDENTIALS", first.path("code").asString());
		assertEquals("The sign-in details are incorrect.", first.path("message").asString());
		for (HttpResponse<String> refusal : refusals) {
			assertEquals(401, refusal.statusCode(), "status " + refusal.statusCode());
			assertEquals(first.path("code"), api.body(refusal).path("code"));
			assertEquals(first.path("message"), api.body(refusal).path("message"));
			assertEquals(first.path("errors"), api.body(refusal).path("errors"));
		}
	}

	/** S-14: a refused account costs the hash a right one costs, so the time says nothing. */
	@Test
	void login_everyRefusal_runsEncoder() throws Exception {
		String unverified = freshAddress();
		String keyless = freshAddress();
		String locked = freshAddress();
		String unverifiedPassword = row(unverified, unverified, false, "ACTIVE");
		String keylessPassword = row(keyless, null, true, "ACTIVE");
		String lockedPassword = row(locked, locked, true, "LOCK");

		for (Map.Entry<String, String> attempt : Map.of(freshAddress(), "whatever", unverified, unverifiedPassword,
				keyless, keylessPassword, locked, lockedPassword).entrySet()) {
			MATCHES.set(0);

			assertEquals(401, login(attempt.getKey(), attempt.getValue()).statusCode());

			assertTrue(MATCHES.get() >= 1, "no hash ran");
		}
	}

	@Test
	void login_unverifiedEmail_touchesNoCounter() throws Exception {
		String unverified = freshAddress();
		String id = row(unverified, unverified, false, "ACTIVE");

		for (int i = 0; i < 6; i++) {
			assertEquals(401, login(unverified, "wrong").statusCode());
		}

		assertEquals(0, failedAttempts(id));
	}

	@Test
	void login_lockedStatus_401_afterHash() throws Exception {
		String locked = freshAddress();
		String id = row(locked, locked, true, "LOCK");

		assertEquals(401, login(locked, id).statusCode(), "the right password");
		assertEquals(401, login(locked, "wrong").statusCode());

		assertEquals(0, failedAttempts(id), "an account that cannot sign in has nothing to count");
		assertEquals("LOCK", jdbc.queryForObject("SELECT user_status FROM users WHERE id = ?", String.class, id));
	}

	@Test
	void login_temporaryLock_423() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		for (int i = 0; i < 4; i++) {
			assertEquals(401, login(user.username(), "wrong").statusCode());
		}

		HttpResponse<String> fifth = login(user.username(), "wrong");
		HttpResponse<String> rightPasswordDuringTheLock = login(user.username(), user.password());

		assertEquals(423, fifth.statusCode(), "status " + fifth.statusCode());
		assertEquals("ACCOUNT_LOCKED", api.body(fifth).path("code").asString());
		assertEquals(423, rightPasswordDuringTheLock.statusCode());
		assertTrue(rightPasswordDuringTheLock.headers().firstValue("Retry-After").isPresent());
	}

	/** D4-10: PENDING with a verified email moves on at its next email sign-in; nothing else changes. */
	@Test
	void login_pendingVerifiedEmail_becomesActive_rolesKept() throws Exception {
		String email = freshAddress();
		String id = row(email, email, true, "PENDING");
		jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'OWNER'", id);

		HttpResponse<String> response = login(email, id);

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		assertEquals("ACTIVE", jdbc.queryForObject("SELECT user_status FROM users WHERE id = ?", String.class, id));
		assertEquals(List.of("OWNER"), jdbc.queryForList(
				"SELECT r.name FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class, id));
	}

	/** S-11: a mailed code opens nothing at login. */
	@Test
	void code_notUsableAtLogin() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		api.post(SEND, null, null, null, api.json().writeValueAsString(Map.of("email", user.username())));

		assertEquals(401, login(user.username(), messages.lastCode(user.username())).statusCode());
	}

	@Test
	void login_noField_400() throws Exception {
		HttpResponse<String> response = api.post(LOGIN, null, null, null,
				api.json().writeValueAsString(Map.of("password", "whatever")));

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("VALIDATION_FAILED", body.path("code").asString());
		assertEquals("email", body.path("errors").get(0).path("field").asString());
	}

	@Test
	void login_bothFields_400() throws Exception {
		HttpResponse<String> response = api.post(LOGIN, null, null, null, api.json().writeValueAsString(
				Map.of("email", "a@example.com", "username", "0901234567", "password", "whatever")));

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertEquals("email", api.body(response).path("errors").get(0).path("field").asString());
	}

	@Test
	void login_refusedEmail_400() throws Exception {
		HttpResponse<String> response = login("not an email", "whatever");

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertNotNull(api.body(response).path("errors").get(0));
	}
}
