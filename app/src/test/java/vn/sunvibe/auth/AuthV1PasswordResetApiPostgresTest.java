package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Session;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * Password reset end to end on PostgreSQL (auth-browser-session spec §3.1 rows 7-9, §3.3, §3.4; W10-1, W10-2, W10-3,
 * W10-8). The per-IP rate limit is off. The codes are read from {@link CapturingMessagesConfig}.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV1PasswordResetApiPostgresTest {

	private static final String RESET_EMAIL = "/api/v1/auth/password-reset/email";
	private static final String RESET_SMS = "/api/v1/auth/password-reset/sms";
	private static final String RESET_COMPLETE = "/api/v1/auth/password-reset/complete";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String NEW_PASSWORD = "Sunvibe@2027";

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

	// ==================== start ====================

	/** W10-2: one shape whether or not the e-mail belongs to an account; no token and no message. */
	@Test
	void resetEmail_knownAndUnknownEmail_answerTheSameShape() throws Exception {
		String known = userWithVerifiedEmail();
		String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

		HttpResponse<String> knownResponse = api.post(RESET_EMAIL, null, null, null, emailBody(known));
		HttpResponse<String> unknownResponse = api.post(RESET_EMAIL, null, null, null, emailBody(unknown));

		assertEquals(200, knownResponse.statusCode(), "status " + knownResponse.statusCode());
		assertEquals(200, unknownResponse.statusCode(), "status " + unknownResponse.statusCode());
		List<String> knownKeys = api.body(knownResponse).propertyNames().stream().sorted().toList();
		List<String> unknownKeys = api.body(unknownResponse).propertyNames().stream().sorted().toList();
		assertEquals(List.of("expiresAt", "otpExpirationMinutes"), knownKeys);
		assertEquals(knownKeys, unknownKeys);
		assertTrue(messages.hasMessage(known), "a code was e-mailed to the account");
		assertFalse(messages.hasMessage(unknown), "nothing sent to an unknown address");
	}

	/** W10-2: the SMS flow answers the reset token and its expiry, without the wrapper and the message. */
	@Test
	void resetSms_answersResetTokenAndExpiry() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");

		HttpResponse<String> response = startSms(user.username());

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("expiresAt", "otpExpirationMinutes", "resetToken"),
				body.propertyNames().stream().sorted().toList());
		assertFalse(body.path("resetToken").asString().isBlank());
		assertTrue(body.path("otpExpirationMinutes").asLong() > 0);
	}

	/** W10-2: a phone number without an account answers the same shape as a known one, and no SMS is sent. */
	@Test
	void resetSms_unknownPhone_answersTheSameShape() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		String unknown = AuthV1ApiTestSupport.nextPhone();

		HttpResponse<String> knownResponse = startSms(user.username());
		HttpResponse<String> unknownResponse = startSms(unknown);

		assertEquals(200, knownResponse.statusCode(), "status " + knownResponse.statusCode());
		assertEquals(200, unknownResponse.statusCode(), "status " + unknownResponse.statusCode());
		JsonNode unknownBody = api.body(unknownResponse);
		assertEquals(api.body(knownResponse).propertyNames().stream().sorted().toList(),
				unknownBody.propertyNames().stream().sorted().toList());
		assertFalse(unknownBody.path("resetToken").asString().isBlank());
		assertTrue(messages.hasMessage(user.username()), "a code was texted to the account");
		assertFalse(messages.hasMessage(unknown), "nothing sent to an unknown number");
	}

	// ==================== complete ====================

	/** W10-1, W10-8: 204, the refresh cookie cleared, and a session opened before is over. */
	@Test
	void resetComplete_rightCode_answers204ClearsAndEndsEverySession() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		Session before = api.logIn(user);
		String resetToken = resetToken(user.username());

		HttpResponse<String> response = complete(resetToken, messages.lastCode(user.username()));

		assertEquals(204, response.statusCode(), "status " + response.statusCode());
		assertTrue(response.body().isEmpty(), "no body");
		List<String> refreshCookies = response.headers().allValues("Set-Cookie").stream()
				.filter(c -> c.startsWith(REFRESH_COOKIE + "="))
				.toList();
		assertEquals(1, refreshCookies.size(), "clears the refresh cookie");
		assertTrue(refreshCookies.get(0).startsWith(REFRESH_COOKIE + "=;"), "empty value");
		assertTrue(refreshCookies.get(0).contains("; Max-Age=0"), "expires now");
		assertTrue(refreshCookies.get(0).contains("; Path=/api/v1/auth/session"), "the cookie's own path");

		HttpResponse<String> refresh = api.post(REFRESH, ORIGIN, before.refreshCookie(), null, null);
		assertEquals(401, refresh.statusCode(), "the old session ended, status " + refresh.statusCode());
		assertEquals("SESSION_EXPIRED", api.body(refresh).path("code").asString());
	}

	/** W10-3: a wrong code with attempts left. */
	@Test
	void resetComplete_wrongCode_answers400CodeIncorrect() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		String resetToken = resetToken(user.username());
		String wrong = messages.lastCode(user.username()).equals("000000") ? "111111" : "000000";

		HttpResponse<String> response = complete(resetToken, wrong);

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("CODE_INCORRECT", body.path("code").asString());
		assertTrue(body.path("errors").isArray() && body.path("errors").isEmpty(), "errors []");
	}

	/** W10-3: an unknown reset token. */
	@Test
	void resetComplete_unknownToken_answers400ResetTokenInvalid() throws Exception {
		HttpResponse<String> response = complete(UUID.randomUUID().toString(), "123456");

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertEquals("RESET_TOKEN_INVALID", api.body(response).path("code").asString());
	}

	/** W10-3: an account locked after the code was sent cannot regain access through the reset. */
	@Test
	void resetComplete_lockedAccount_answers401AccountDisabled() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		String resetToken = resetToken(user.username());
		String code = messages.lastCode(user.username());
		assertEquals(1, jdbc.update("update users set user_status = 'LOCK' where id = ?", user.userId()));

		HttpResponse<String> response = complete(resetToken, code);

		assertEquals(401, response.statusCode(), "status " + response.statusCode());
		assertEquals("ACCOUNT_DISABLED", api.body(response).path("code").asString());
	}

	// ==================== helpers ====================

	/** An active account whose e-mail is verified, so the e-mail flow sends it a code. */
	private String userWithVerifiedEmail() {
		String email = "w10-" + UUID.randomUUID() + "@example.com";
		Instant now = AppClock.currentUTC();
		AppUser user = AppUser.create(UserId.create(ulidGenerator.generateId()), "W10 user",
				Username.create(AuthV1ApiTestSupport.nextPhone()), Email.create(email),
				Password.create(passwordEncoder.encode(UUID.randomUUID().toString())), ProfileImage.create(null), null,
				new HashSet<>(Set.of("TENANT", "CUSTOMER")), now);
		user.verifyPhone(now);
		user.verifyEmail(now);
		user.setUserStatus(UserStatus.ACTIVE);
		userRepository.save(user);
		return email;
	}

	private String emailBody(String email) throws Exception {
		return api.json().writeValueAsString(Map.of("email", email));
	}

	private HttpResponse<String> startSms(String phone) throws Exception {
		return api.post(RESET_SMS, null, null, null, api.json().writeValueAsString(Map.of("phoneNumber", phone)));
	}

	private String resetToken(String phone) throws Exception {
		HttpResponse<String> started = startSms(phone);
		assertEquals(200, started.statusCode(), "reset start failed, status " + started.statusCode());
		return api.body(started).path("resetToken").asString();
	}

	private HttpResponse<String> complete(String resetToken, String code) throws Exception {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("resetToken", resetToken);
		body.put("otpCode", code);
		body.put("newPassword", NEW_PASSWORD);
		body.put("confirmPassword", NEW_PASSWORD);
		return api.post(RESET_COMPLETE, null, null, null, api.json().writeValueAsString(body));
	}
}
