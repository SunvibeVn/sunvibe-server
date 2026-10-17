package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Registration by phone end to end on PostgreSQL (auth-browser-session spec §3.1 rows 4-6, §3.3, §3.4, §3.5; W10-1,
 * W10-2, W10-3, W10-8). The per-IP rate limit is off; the code cooldown of the use case still applies. The codes are
 * read from {@link CapturingMessagesConfig}.
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
class AuthV1RegistrationApiPostgresTest {

	private static final String SEND_CODE = "/api/v1/auth/registration/phone/send-code";
	private static final String VERIFY_CODE = "/api/v1/auth/registration/phone/verify-code";
	private static final String REGISTER = "/api/v1/auth/registration";
	private static final String PASSWORD = "Sunvibe@2026";

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

	// ==================== send-code ====================

	/** W10-2: the code-sent body without the legacy wrapper. */
	@Test
	void sendCode_newPhone_answersCodeSentWithoutWrapper() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();

		HttpResponse<String> response = sendCode(phone);

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("channel", "expiresAt", "resendAvailableAt", "sentTo"),
				body.propertyNames().stream().sorted().toList());
		assertEquals("PHONE", body.path("channel").asString());
		assertFalse(body.path("sentTo").asString().contains(phone), "masked");
		assertTrue(messages.hasMessage(phone), "a code was sent");
	}

	/** W10-3: a taken number is a field refusal on username, 400 VALIDATION_FAILED (legacy 409). */
	@Test
	void sendCode_phoneInUse_answers400ValidationFailedOnUsername() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");

		HttpResponse<String> response = sendCode(user.username());

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("VALIDATION_FAILED", body.path("code").asString());
		assertEquals("username", body.path("errors").path(0).path("field").asString());
		assertFalse(body.toString().contains(user.username()), "never echoes the submitted value");
		assertFalse(messages.hasMessage(user.username()), "no code for a taken number");
	}

	/** W10-3: the use-case cooldown (not the per-IP limiter) answers 429 with Retry-After. */
	@Test
	void sendCode_againWithinCooldown_answers429CodeResendTooSoonWithRetryAfter() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		assertEquals(200, sendCode(phone).statusCode());

		HttpResponse<String> response = sendCode(phone);

		assertEquals(429, response.statusCode(), "status " + response.statusCode());
		assertEquals("CODE_RESEND_TOO_SOON", api.body(response).path("code").asString());
		String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
		assertNotNull(retryAfter, "Retry-After");
		assertTrue(Long.parseLong(retryAfter) > 0, "seconds to wait");
	}

	// ==================== verify-code ====================

	/** W10-3: a wrong code with attempts left. */
	@Test
	void verifyCode_wrongCode_answers400CodeIncorrect() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		assertEquals(200, sendCode(phone).statusCode());
		String wrong = messages.lastCode(phone).equals("000000") ? "111111" : "000000";

		HttpResponse<String> response = verifyCode(phone, wrong);

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("CODE_INCORRECT", body.path("code").asString());
		assertTrue(body.path("errors").isArray() && body.path("errors").isEmpty(), "errors []");
	}

	/** W10-2: the registration token body, as today but without the wrapper. */
	@Test
	void verifyCode_rightCode_answersRegistrationToken() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		assertEquals(200, sendCode(phone).statusCode());

		HttpResponse<String> response = verifyCode(phone, messages.lastCode(phone));

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("expiresAt", "registrationToken"), body.propertyNames().stream().sorted().toList());
		assertFalse(body.path("registrationToken").asString().isBlank());
	}

	// ==================== registration ====================

	/** W10-1, W10-2, W10-8: 201 with the session body and only the narrow refresh cookie (spec C-6). */
	@Test
	void register_withToken_answers201SessionAndTheNarrowCookie() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		String token = registrationToken(phone);

		HttpResponse<String> response = register(phone, token);

		assertEquals(201, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("accessToken", "expiresIn", "tokenType", "user"),
				body.propertyNames().stream().sorted().toList());
		assertEquals("Bearer", body.path("tokenType").asString());
		assertFalse(body.path("accessToken").asString().isBlank());
		assertEquals(900, body.path("expiresIn").asInt());
		JsonNode user = body.path("user");
		assertFalse(user.path("userId").asString().isBlank());
		assertEquals(phone, user.path("username").asString());
		assertEquals(List.of("CUSTOMER", "TENANT"), strings(user.path("roles")));
		assertTrue(user.path("phoneVerified").asBoolean(), "phoneVerified");
		assertFalse(body.toString().contains("refreshToken"), "never the refresh token in the body");

		List<String> setCookies = response.headers().allValues("Set-Cookie");
		List<String> refreshCookies = setCookies.stream().filter(c -> c.startsWith(REFRESH_COOKIE + "=")).toList();
		assertEquals(1, refreshCookies.size(), "one refresh cookie");
		String cookie = refreshCookies.get(0);
		assertTrue(cookie.contains("; Path=/api/v1/auth/session"), "narrow path");
		assertTrue(cookie.contains("; Max-Age=604800"), "seven days");
		assertTrue(cookie.contains("; HttpOnly"), "HttpOnly");
		assertTrue(cookie.contains("; SameSite=Strict"), "SameSite=Strict");
		assertFalse(cookie.toLowerCase().contains("domain="), "host-only");
		assertTrue(setCookies.stream().noneMatch(c -> c.startsWith("cookie-jwt")), "no legacy cookie");
	}

	/**
	 * W10-3: a registration token works once. The first account is soft-deleted, so the phone is free again and only
	 * the used token can refuse the second registration.
	 */
	@Test
	void register_usedToken_answers400RegistrationTokenInvalid() throws Exception {
		String phone = AuthV1ApiTestSupport.nextPhone();
		String token = registrationToken(phone);
		HttpResponse<String> first = register(phone, token);
		assertEquals(201, first.statusCode());
		String firstUserId = api.body(first).path("user").path("userId").asString();
		assertEquals(1, jdbc.update("update users set deleted_at = now() where id = ?", firstUserId));

		HttpResponse<String> response = register(phone, token);

		assertEquals(400, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("REGISTRATION_TOKEN_INVALID", body.path("code").asString());
		assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "no cookie");
	}

	// ==================== helpers ====================

	private HttpResponse<String> sendCode(String phone) throws Exception {
		return api.post(SEND_CODE, null, null, null, api.json().writeValueAsString(Map.of("phoneNumber", phone)));
	}

	private HttpResponse<String> verifyCode(String phone, String code) throws Exception {
		return api.post(VERIFY_CODE, null, null, null,
				api.json().writeValueAsString(Map.of("phoneNumber", phone, "code", code)));
	}

	private String registrationToken(String phone) throws Exception {
		assertEquals(200, sendCode(phone).statusCode());
		HttpResponse<String> verified = verifyCode(phone, messages.lastCode(phone));
		assertEquals(200, verified.statusCode(), "verify failed, status " + verified.statusCode());
		return api.body(verified).path("registrationToken").asString();
	}

	private HttpResponse<String> register(String phone, String registrationToken) throws Exception {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("fullName", "W10 registrant");
		body.put("username", phone);
		body.put("password", PASSWORD);
		body.put("confirmPassword", PASSWORD);
		body.put("registrationToken", registrationToken);
		return api.post(REGISTER, null, null, null, api.json().writeValueAsString(body));
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}
}
