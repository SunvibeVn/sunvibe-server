package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Session;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Password;
import vn.sunvibe.auth.core.domain.model.user.vo.ProfileImage;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.model.user.vo.Username;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

/**
 * The signed-in user's own account end to end on PostgreSQL (auth-browser-session spec §3.1 rows 10-16, §3.3, §3.4,
 * §3.9; W10-1, W10-3, W10-13). The per-IP rate limit is off. The codes are read from {@link CapturingMessagesConfig}.
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
class MeApiPostgresTest {

	private static final String ME = "/api/v1/me";
	private static final String PHONE_SEND = "/api/v1/me/verification/phone/send-code";
	private static final String PHONE_CONFIRM = "/api/v1/me/verification/phone/confirm-code";
	private static final String EMAIL_SEND = "/api/v1/me/verification/email/send-code";
	private static final String EMAIL_CONFIRM = "/api/v1/me/verification/email/confirm-code";
	private static final String PASSWORD_CHANGE = "/api/v1/me/password/change";
	private static final String LOGOUT_ALL = "/api/v1/me/sessions/logout-all";
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

	private AuthV1ApiTestSupport api;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
	}

	@AfterAll
	void tearDown() {
		api.close();
	}

	// ==================== profile ====================

	/** W10-1: today's profile fields, without the wrapper. */
	@Test
	void me_withBearer_answersProfileWithoutWrapper() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		Session session = api.logIn(user);

		HttpResponse<String> response = api.get(ME, session.accessToken());

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("accountStatus", "email", "emailVerified", "fullName", "kycStatus", "phoneVerified",
				"profileImageUrl", "username"), body.propertyNames().stream().sorted().toList());
		assertEquals(user.username(), body.path("username").asString());
		assertEquals("ACTIVE", body.path("accountStatus").asString());
		assertTrue(body.path("phoneVerified").asBoolean());
		assertFalse(body.path("emailVerified").asBoolean());
	}

	/** spec §3.9: the default rule of the security chain answers, with its own body. */
	@Test
	void me_withoutBearer_answers401SecurityError() throws Exception {
		HttpResponse<String> response = api.get(ME, null);

		assertEquals(401, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("Unauthorized", body.path("error").asString());
		assertEquals(ME, body.path("path").asString());
	}

	/**
	 * spec §3.9: the explicit rule for {@code /api/v1/me/**} answers every one of the seven operations without a
	 * Bearer token with the chain's 401 {@code SecurityError} body (error, message, path).
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"GET " + ME,
		"POST " + PHONE_SEND,
		"POST " + PHONE_CONFIRM,
		"POST " + EMAIL_SEND,
		"POST " + EMAIL_CONFIRM,
		"POST " + PASSWORD_CHANGE,
		"POST " + LOGOUT_ALL })
	void meOperation_withoutBearer_answers401SecurityError(String operation) throws Exception {
		String[] parts = operation.split(" ");
		String path = parts[1];

		HttpResponse<String> response = "GET".equals(parts[0])
				? api.get(path, null)
				: api.post(path, ORIGIN, null, null, "{}");

		assertEquals(401, response.statusCode(), operation + " status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("Unauthorized", body.path("error").asString(), operation);
		assertFalse(body.path("message").asString().isBlank(), operation);
		assertEquals(path, body.path("path").asString(), operation);
	}

	/** The default rule of the chain still covers an unlisted v1 auth path: anonymous, it answers 401. */
	@Test
	void unlistedAuthPath_anonymousPost_answers401() throws Exception {
		HttpResponse<String> response = api.post("/api/v1/auth/not-a-path", ORIGIN, null, null, "{}");

		assertEquals(401, response.statusCode(), "status " + response.statusCode());
	}

	/** W10-13, auth-browser-session BR-8: a profile body is never cached. */
	@Test
	void me_answer_hasNoStore() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.get(ME, session.accessToken());

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"),
				"Cache-Control " + response.headers().firstValue("Cache-Control").orElse(""));
	}

	// ==================== contact verification ====================

	/** W10-1: the code texted to the phone verifies it; the PENDING account becomes ACTIVE. */
	@Test
	void phoneConfirm_rightCode_answers204() throws Exception {
		Created user = userWithUnverifiedPhone();
		Session session = api.logIn(user);
		HttpResponse<String> sent = api.post(PHONE_SEND, null, null, session.accessToken(), null);
		assertEquals(200, sent.statusCode(), "send status " + sent.statusCode());
		assertEquals(List.of("channel", "expiresAt", "resendAvailableAt", "sentTo"),
				api.body(sent).propertyNames().stream().sorted().toList());

		HttpResponse<String> response = api.post(PHONE_CONFIRM, null, null, session.accessToken(),
				codeBody(messages.lastCode(user.username())));

		assertEquals(204, response.statusCode(), "status " + response.statusCode());
		assertTrue(response.body().isEmpty(), "no body");
		JsonNode me = api.body(api.get(ME, session.accessToken()));
		assertTrue(me.path("phoneVerified").asBoolean());
		assertEquals("ACTIVE", me.path("accountStatus").asString());
	}

	/** W10-3 */
	@Test
	void phoneSend_alreadyVerified_answers400AlreadyVerified() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(PHONE_SEND, null, null, session.accessToken(), null);

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("ALREADY_VERIFIED", body.path("code").asString());
		assertTrue(body.path("errors").isArray() && body.path("errors").isEmpty(), "errors []");
	}

	/** W10-3 */
	@Test
	void emailSend_noEmail_answers400NoEmail() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(EMAIL_SEND, null, null, session.accessToken(), null);

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		assertEquals("NO_EMAIL", api.body(response).path("code").asString());
	}

	// ==================== password change ====================

	/** W10-3, spec §3.4: a wrong current password is a field refusal, never a 401 the client would refresh on. */
	@Test
	void passwordChange_wrongCurrent_answers400OnCurrentPassword() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(PASSWORD_CHANGE, null, null, session.accessToken(),
				passwordChangeBody(UUID.randomUUID().toString(), NEW_PASSWORD));

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("VALIDATION_FAILED", body.path("code").asString());
		assertEquals(1, body.path("errors").size());
		assertEquals("currentPassword", body.path("errors").get(0).path("field").asString());
	}

	/** W10-1: a new session for this device in the body and the cookie; the session opened before is over. */
	@Test
	void passwordChange_right_answers200SessionAndANewCookie() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		Session before = api.logIn(user);

		HttpResponse<String> response = api.post(PASSWORD_CHANGE, null, null, before.accessToken(),
				passwordChangeBody(user.password(), NEW_PASSWORD));

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("accessToken", "expiresIn", "tokenType", "user"),
				body.propertyNames().stream().sorted().toList());
		assertEquals("Bearer", body.path("tokenType").asString());
		assertEquals(user.userId(), body.path("user").path("userId").asString());
		List<String> setCookies = response.headers().allValues("Set-Cookie");
		String newValue = AuthV1ApiTestSupport.cookieValue(setCookies, REFRESH_COOKIE);
		assertTrue(newValue != null && !newValue.isEmpty(), "sets a refresh cookie");
		String newCookie = REFRESH_COOKIE + "=" + newValue;
		assertNotEquals(before.refreshCookie(), newCookie, "a new session");
		assertFalse(setCookies.stream().anyMatch(c -> c.startsWith("cookie-jwt")), "no legacy cookies");

		assertEquals(401, api.post(REFRESH, ORIGIN, before.refreshCookie(), null, null).statusCode(),
				"the old session ended");
		assertEquals(200, api.post(REFRESH, ORIGIN, newCookie, null, null).statusCode(),
				"the new session works");
	}

	// ==================== logout-all ====================

	/** W10-1: every session of the account ends and this device's refresh cookie is cleared. */
	@Test
	void logoutAll_answers204ClearsAndEndsEverySession() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		Session first = api.logIn(user);
		Session second = api.logIn(user);

		HttpResponse<String> response = api.post(LOGOUT_ALL, null, null, first.accessToken(), null);

		assertEquals(204, response.statusCode(), "status " + response.statusCode());
		assertTrue(response.body().isEmpty(), "no body");
		List<String> refreshCookies = response.headers().allValues("Set-Cookie").stream()
				.filter(c -> c.startsWith(REFRESH_COOKIE + "="))
				.toList();
		assertEquals(1, refreshCookies.size(), "clears the refresh cookie");
		assertTrue(refreshCookies.get(0).startsWith(REFRESH_COOKIE + "=;"), "empty value");
		assertTrue(refreshCookies.get(0).contains("; Max-Age=0"), "expires now");
		assertTrue(refreshCookies.get(0).contains("; Path=/api/v1/auth/session"), "the cookie's own path");
		assertEquals(401, api.post(REFRESH, ORIGIN, first.refreshCookie(), null, null).statusCode(),
				"this device's session ended");
		assertEquals(401, api.post(REFRESH, ORIGIN, second.refreshCookie(), null, null).statusCode(),
				"the other device's session ended");
	}

	// ==================== helpers ====================

	/** A just-registered account: PENDING, phone not verified yet, no e-mail. */
	private Created userWithUnverifiedPhone() {
		String username = AuthV1ApiTestSupport.nextPhone();
		String password = UUID.randomUUID().toString();
		Instant now = AppClock.currentUTC();
		AppUser user = AppUser.create(UserId.create(ulidGenerator.generateId()), "W10 user", Username.create(username),
				null, Password.create(passwordEncoder.encode(password)), ProfileImage.create(null), null,
				new HashSet<>(Set.of("TENANT", "CUSTOMER")), now);
		return new Created(userRepository.save(user).getId().getValue(), username, password);
	}

	private String codeBody(String code) throws Exception {
		return api.json().writeValueAsString(Map.of("code", code));
	}

	private String passwordChangeBody(String current, String newPassword) throws Exception {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("currentPassword", current);
		body.put("newPassword", newPassword);
		body.put("confirmPassword", newPassword);
		return api.json().writeValueAsString(body);
	}
}
