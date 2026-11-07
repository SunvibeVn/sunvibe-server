package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.core.application.shared.external.email.MailSendGatePort;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Email registration end to end on PostgreSQL (auth-email-access 4.7, 4.8, S-7..S-13): the same answer for every address
 * until the mailbox is proven, and a mail that fails leaves nothing behind. Phone mode is off here, as in production:
 * none of this depends on the phone channel.
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
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV1EmailRegistrationApiPostgresTest {

	private static final String SEND = "/api/v1/auth/registration/email/send-code";
	private static final String VERIFY = "/api/v1/auth/registration/email/verify-code";
	private static final String COMPLETE = "/api/v1/auth/registration/email";
	private static final String ME = "/api/v1/me";
	private static final String STRONG_PASSWORD = "Sunvibe@2027";

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
	private MailSendGatePort mailGate;

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

	@AfterEach
	void mailBack() {
		messages.mailDown(false);
	}

	private static String freshAddress() {
		return "reg-" + UUID.randomUUID() + "@example.com";
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	/** Wrong codes commit although each request fails, and the fifth burns the proof. */
	@Test
	void verify_fiveWrongCodes_burned() throws Exception {
		String email = freshAddress();
		assertEquals(200, send(email).statusCode());
		String code = messages.lastCode(email);
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 4; i++) {
			assertCode(verify(email, wrong), 400, "CODE_INCORRECT");
		}
		assertCode(verify(email, wrong), 400, "CODE_ATTEMPTS_EXCEEDED");

		assertCode(verify(email, code), 400, "CODE_EXPIRED");
		assertEquals(5, jdbc.queryForObject("SELECT failed_attempts FROM registration_email_challenges WHERE email_key = ?",
				Integer.class, email));
	}

	private HttpResponse<String> send(String email) throws Exception {
		return api.post(SEND, null, null, null, json(Map.of("email", email)));
	}

	private HttpResponse<String> verify(String email, String code) throws Exception {
		return api.post(VERIFY, null, null, null, json(Map.of("email", email, "code", code)));
	}

	private HttpResponse<String> complete(String email, String token) throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("email", email, "registrationToken", token, "fullName", "Nguyen Van A",
				"password", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD));
		return api.post(COMPLETE, null, null, null, json(body));
	}

	private String proven(String email) throws Exception {
		assertEquals(200, send(email).statusCode());
		HttpResponse<String> verified = verify(email, messages.lastCode(email));
		assertEquals(200, verified.statusCode(), "status " + verified.statusCode());
		return api.body(verified).path("registrationToken").asString();
	}

	private void assertCode(HttpResponse<String> response, int status, String code) {
		assertEquals(status, response.statusCode(), "status " + response.statusCode());
		assertEquals(code, api.body(response).path("code").asString());
	}

	private int challengeRows(String key) {
		return jdbc.queryForObject("SELECT count(*) FROM registration_email_challenges WHERE email_key = ?",
				Integer.class, key);
	}

	@Test
	void send_sameResponse_freeVerifiedUnverifiedKeyless() throws Exception {
		Created verified = api.createEmailUser("CUSTOMER");
		String unverified = freshAddress();
		String keyless = freshAddress();
		insertRow(unverified, unverified, null);
		insertRow(keyless.toUpperCase(), null, null);

		List<JsonNode> bodies = new java.util.ArrayList<>();
		for (String email : List.of(freshAddress(), verified.username(), unverified, keyless)) {
			HttpResponse<String> response = send(email);
			assertEquals(200, response.statusCode(), "status " + response.statusCode());
			assertTrue(messages.hasMessage(email), "a real code goes out");
			bodies.add(api.body(response));
		}

		for (JsonNode body : bodies) {
			assertEquals(List.of("channel", "expiresAt", "resendAvailableAt", "sentTo"),
					body.propertyNames().stream().sorted().toList());
			assertEquals("EMAIL", body.path("channel").asString());
		}
	}

	@Test
	void send_mailFails_503_noRow() throws Exception {
		String email = freshAddress();
		messages.mailDown(true);

		HttpResponse<String> failed = send(email);

		assertCode(failed, 503, "EMAIL_DELIVERY_FAILED");
		assertEquals("60", failed.headers().firstValue("Retry-After").orElseThrow());
		assertEquals(0, challengeRows(email), "no row, so no cooldown either");

		messages.mailDown(false);
		assertEquals(200, send(email).statusCode(), "a retry at once");
	}

	@Test
	void send_cooldown_429() throws Exception {
		String email = freshAddress();
		send(email);

		HttpResponse<String> again = send(email);

		assertCode(again, 429, "CODE_RESEND_TOO_SOON");
		assertTrue(again.headers().firstValue("Retry-After").isPresent());
	}

	@Test
	void send_bulkheadFull_503() throws Exception {
		int taken = 0;
		while (mailGate.tryEnter()) {
			taken++;
		}
		try {
			assertCode(send(freshAddress()), 503, "EMAIL_DELIVERY_FAILED");
		} finally {
			for (int i = 0; i < taken; i++) {
				mailGate.leave();
			}
		}
	}

	@Test
	void verify_free_returnsToken() throws Exception {
		String email = freshAddress();
		send(email);

		HttpResponse<String> response = verify(email.toUpperCase(), messages.lastCode(email));

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		assertFalse(api.body(response).path("registrationToken").asString().isBlank());
		String stored = jdbc.queryForObject("SELECT token_hash FROM registration_email_challenges WHERE email_key = ?",
				String.class, email);
		assertNotNull(stored);
		assertFalse(stored.equals(api.body(response).path("registrationToken").asString()), "only the hash is stored");
	}

	@Test
	void verify_verifiedAccount_409Exists_codeUsed() throws Exception {
		Created user = api.createEmailUser("CUSTOMER");
		send(user.username());
		String code = messages.lastCode(user.username());

		assertCode(verify(user.username(), code), 409, "EMAIL_ACCOUNT_EXISTS");

		assertCode(verify(user.username(), code), 400, "CODE_EXPIRED");
	}

	@Test
	void verify_unverifiedOrKeyless_409NotAvailable() throws Exception {
		String unverified = freshAddress();
		String keyless = freshAddress();
		insertRow(unverified, unverified, null);
		insertRow("  " + keyless.toUpperCase() + " ", null, null);
		send(unverified);
		send(keyless);

		assertCode(verify(unverified, messages.lastCode(unverified)), 409, "EMAIL_NOT_AVAILABLE");
		assertCode(verify(keyless, messages.lastCode(keyless)), 409, "EMAIL_NOT_AVAILABLE");
	}

	@Test
	void complete_setsCookie_201() throws Exception {
		String email = freshAddress();
		String token = proven(email);

		HttpResponse<String> created = complete(email, token);

		assertEquals(201, created.statusCode(), "status " + created.statusCode());
		JsonNode body = api.body(created);
		assertTrue(body.path("user").path("username").isNull());
		assertEquals(email, body.path("user").path("email").asString());
		assertTrue(body.path("user").path("emailVerified").asBoolean());
		assertEquals(List.of("CUSTOMER", "TENANT"), body.path("user").path("roles").valueStream()
				.map(JsonNode::asString).sorted().toList());
		assertNotNull(AuthV1ApiTestSupport.cookieValue(created.headers().allValues("Set-Cookie"), REFRESH_COOKIE));
		assertEquals(200, api.get(ME, body.path("accessToken").asString()).statusCode(), "the session works");
	}

	@Test
	void complete_tokenOtherEmail_refused() throws Exception {
		String token = proven(freshAddress());

		assertCode(complete(freshAddress(), token), 400, "REGISTRATION_TOKEN_INVALID");
	}

	@Test
	void complete_sameTokenTwice_secondRefused() throws Exception {
		String email = freshAddress();
		String token = proven(email);
		assertEquals(201, complete(email, token).statusCode());

		assertCode(complete(email, token), 400, "REGISTRATION_TOKEN_INVALID");

		assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email_key = ?", Integer.class, email));
	}

	/** S-11: a code is no token: the mailbox proof is exchanged, never replayed. */
	@Test
	void code_notUsableAsToken() throws Exception {
		String email = freshAddress();
		send(email);

		assertCode(complete(email, messages.lastCode(email)), 400, "REGISTRATION_TOKEN_INVALID");

		assertNull(jdbc.queryForObject("SELECT verified_at FROM registration_email_challenges WHERE email_key = ?",
				Timestamp.class, email));
	}

	/** E-17: the address is taken between the proof and the account: 409, no session, and the token is still there. */
	@Test
	void complete_conflict_409_noCookie_tokenNotConsumed() throws Exception {
		String email = freshAddress();
		String token = proven(email);
		insertRow(email, email, null);

		HttpResponse<String> response = complete(email, token);

		assertCode(response, 409, "EMAIL_NOT_AVAILABLE");
		assertNull(AuthV1ApiTestSupport.cookieValue(response.headers().allValues("Set-Cookie"), REFRESH_COOKIE));
		assertNull(jdbc.queryForObject("SELECT registered_at FROM registration_email_challenges WHERE email_key = ?",
				Timestamp.class, email));
	}

	@Test
	void complete_fullNameWithControlCharacter_400() throws Exception {
		String email = freshAddress();
		String token = proven(email);
		Map<String, Object> body = new HashMap<>(Map.of("email", email, "registrationToken", token,
				"fullName", "Nguyen\u0000 Van A", "password", STRONG_PASSWORD, "confirmPassword", STRONG_PASSWORD));

		assertCode(api.post(COMPLETE, null, null, null, json(body)), 400, "VALIDATION_FAILED");
	}

	/** A row as a test wants it: a stored address, with or without a key. */
	private void insertRow(String email, String emailKey, Timestamp verifiedAt) {
		Timestamp now = Timestamp.from(AppClock.currentUTC());
		jdbc.update("INSERT INTO users (id, full_name, email, email_key, email_verified_at, password, kyc_status,"
				+ " user_status, created_date, last_update) VALUES (?, 'Other row', ?, ?, ?, 'hash', 'UNVERIFIED', 'ACTIVE',"
				+ " ?, ?)", ulidGenerator.generateId(), email, emailKey, verifiedAt, now, now);
	}
}
