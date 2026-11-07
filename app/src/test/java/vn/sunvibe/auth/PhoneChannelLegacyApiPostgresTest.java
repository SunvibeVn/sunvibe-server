package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.core.application.shared.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Phone mode {@code legacy-login} (auth-email-access spec 4.5, 5.5, D4-1, D4-7, S-34): phone + password only, for
 * accounts without a verified email, so they can reach the bind step. Every other phone operation stays closed, and a
 * row without a key keeps its way in by its exact stored string.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"app.auth.phone.mode=legacy-login",
			"app.sms.provider=none" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhoneChannelLegacyApiPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

	@DynamicPropertySource
	static void legacyEndDate(DynamicPropertyRegistry registry) {
		registry.add("app.auth.phone.legacy-login-until", () -> AppClock.currentUTC().plus(Duration.ofDays(30)).toString());
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
	private AccessTokenIssuerPort accessTokens;

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

	/** A row as an older release left it: a stored username and no phone key. */
	private String keylessRow(String username, String password) {
		String id = ulidGenerator.generateId();
		Timestamp now = Timestamp.from(AppClock.currentUTC());
		jdbc.update("INSERT INTO users (id, full_name, username, password, kyc_status, user_status, created_date,"
				+ " last_update) VALUES (?, 'Legacy row', ?, ?, 'UNVERIFIED', 'ACTIVE', ?, ?)", id, username,
				passwordEncoder.encode(password), now, now);
		return id;
	}

	private HttpResponse<String> login(String username, String password) throws Exception {
		return api.post(LOGIN, null, null, null, api.loginBody(username, password));
	}

	@Test
	void legacy_phoneOnly_signsIn() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = login(user.username(), user.password());

		assertEquals(200, response.statusCode());
		assertEquals("BIND_EMAIL", api.body(response).path("user").path("requiredAction").asString(),
				"auth-email-access 4.6: the way in is closing, so the account is told");
	}

	@Test
	void legacy_keylessRow_byStoredString() throws Exception {
		String stored = String.format("0986%06d", SEQUENCE.getAndIncrement());
		String password = UUID.randomUUID().toString();
		keylessRow(stored, password);

		assertEquals(200, login(stored, password).statusCode());
	}

	/** A shape the policy refuses (a trunk 0 after the country code) is only ever that exact string. */
	@Test
	void legacy_valueRefusedByPolicy_oldShape_found() throws Exception {
		String stored = String.format("840986%06d", SEQUENCE.getAndIncrement());
		String password = UUID.randomUUID().toString();
		keylessRow(stored, password);

		assertEquals(200, login(stored, password).statusCode());
	}

	@Test
	void legacy_nulByte_refused() throws Exception {
		HttpResponse<String> response = login("0901234567\u0000", "whatever");

		assertEquals(401, response.statusCode(), "status " + response.statusCode());
		assertEquals("INVALID_CREDENTIALS", api.body(response).path("code").asString());
	}

	/** D4-1: an account that can use its email counts as not found here: 401, and not counted. */
	@Test
	void legacy_verifiedEmail_401() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String email = "legacy-" + user.userId().toLowerCase() + "@example.com";
		jdbc.update("UPDATE users SET email = ?, email_key = ?, email_verified_at = ? WHERE id = ?", email, email,
				Timestamp.from(AppClock.currentUTC()), user.userId());

		HttpResponse<String> response = login(user.username(), user.password());

		assertEquals(401, response.statusCode(), "status " + response.statusCode());
		assertEquals(0, jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
				user.userId()), "not counted");
	}

	@Test
	void legacy_otherPhoneOperations_403() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String phoneBody = api.json().writeValueAsString(Map.of("phoneNumber", user.username()));
		String token = accessTokens.issue(userRepository.findById(UserId.from(user.userId())).orElseThrow());

		for (HttpResponse<String> response : java.util.List.of(
				api.post("/api/v1/auth/registration/phone/send-code", null, null, null, phoneBody),
				api.post("/api/v1/auth/password-reset/sms", null, null, null, phoneBody),
				api.post("/api/v1/me/verification/phone/send-code", null, null, token, null))) {
			assertEquals(403, response.statusCode(), "status " + response.statusCode());
			assertEquals("AUTH_CHANNEL_DISABLED", api.body(response).path("code").asString());
		}
		assertFalse(jdbc.queryForObject("SELECT count(*) > 0 FROM verification_codes WHERE user_id = ?", Boolean.class,
				user.userId()));
	}
}
