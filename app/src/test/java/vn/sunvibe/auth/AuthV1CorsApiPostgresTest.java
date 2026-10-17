package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.cookieValue;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * CORS of the browser-session API end to end on PostgreSQL (auth-browser-session spec §3.1 namespace rules, §3.7;
 * W10-9, W10-10): the security chain applies the {@code app.cors.allowed-origins} allow-list, so the chain's 401/403,
 * the refresh 401 and the v1 error bodies all carry the CORS headers for an allowed origin, and a not allowed origin
 * is refused before any controller runs. The rate limit is off; its 429 is covered by RateLimitFilterTest.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthV1CorsApiPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String FOREIGN_ORIGIN = "https://evil.example";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

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

	// ==================== preflight ====================

	/** W10-9: the page may send the Bearer token and a JSON body. */
	@Test
	void preflight_allowedOrigin_allowsAuthorizationAndContentType() throws Exception {
		HttpResponse<String> response = api.send(preflight("/api/v1/me/password/change", ORIGIN));

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		assertEquals(ORIGIN, header(response, "Access-Control-Allow-Origin"));
		assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
		List<String> allowedHeaders = lowerCaseList(header(response, "Access-Control-Allow-Headers"));
		assertTrue(allowedHeaders.contains("authorization"), allowedHeaders.toString());
		assertTrue(allowedHeaders.contains("content-type"), allowedHeaders.toString());
		assertTrue(lowerCaseList(header(response, "Access-Control-Allow-Methods")).contains("post"));
	}

	/** W10-9: never "*"; another origin gets 403 and no Access-Control-Allow-Origin. */
	@Test
	void preflight_foreignOrigin_isRefused() throws Exception {
		HttpResponse<String> response = api.send(preflight(LOGIN, FOREIGN_ORIGIN));

		assertEquals(403, response.statusCode());
		assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
		assertTrue(response.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
	}

	// ==================== error answers carry CORS ====================

	/** W10-9: without CORS headers the browser reports the 401 as a network error and the client never refreshes. */
	@Test
	void entryPoint401_fromAllowedOrigin_carriesCorsHeaders() throws Exception {
		HttpResponse<String> response = api.send(api.request("/api/v1/me").header("Origin", ORIGIN).GET());

		assertEquals(401, response.statusCode());
		assertCors(response);
	}

	/** W10-9: the 403 for a signed-in caller without the role. */
	@Test
	void accessDenied403_fromAllowedOrigin_carriesCorsHeaders() throws Exception {
		Session tenant = api.logIn(api.createUser(UserStatus.ACTIVE, "TENANT"));
		Created target = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = api.send(api.request("/api/v1/staff/users/" + target.userId() + "/roles")
				.header("Origin", ORIGIN)
				.header("Authorization", "Bearer " + tenant.accessToken())
				.GET());

		assertEquals(403, response.statusCode());
		assertCors(response);
	}

	/** W10-9, spec §3.7: the refresh 401 carries CORS and the clearing Set-Cookie. */
	@Test
	void refresh401_fromAllowedOrigin_carriesCorsAndTheClearingCookie() throws Exception {
		HttpResponse<String> response = api.post(REFRESH, ORIGIN, null, null, null);

		assertEquals(401, response.statusCode());
		assertEquals("SESSION_EXPIRED", api.body(response).path("code").asString());
		assertCors(response);
		List<String> setCookies = response.headers().allValues("Set-Cookie");
		String cleared = setCookies.stream().filter(c -> c.startsWith(REFRESH_COOKIE + "=")).findFirst().orElse(null);
		assertNotNull(cleared, "a clearing Set-Cookie");
		assertEquals("", cookieValue(setCookies, REFRESH_COOKIE));
		assertTrue(cleared.contains("; Path=/api/v1/auth/session"), "same path");
		assertTrue(cleared.contains("; Max-Age=0"), "expired");
	}

	/** W10-9: an ErrorResponse written by the v1 handler. */
	@Test
	void v1Error400_fromAllowedOrigin_carriesCors() throws Exception {
		HttpResponse<String> response = api.post(LOGIN, ORIGIN, null, null, "{}");

		assertEquals(400, response.statusCode());
		assertEquals("VALIDATION_FAILED", api.body(response).path("code").asString());
		assertCors(response);
	}

	// ==================== login CSRF (spec §3.1 namespace rules, auth-browser-session BR-7) ====================

	/**
	 * A cross-origin page can send a text/plain POST without a preflight; CORS refuses the not allowed origin before
	 * the controller, so the login use case never runs (no failed attempt is counted, no session is opened).
	 */
	@Test
	void textPlainLogin_fromForeignOrigin_isRefusedAndRunsNothing() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String wrongPassword = api.loginBody(user.username(), "Wrong-password-1");
		String rightPassword = api.loginBody(user.username(), user.password());

		HttpResponse<String> wrong = api.send(textPlainLogin(FOREIGN_ORIGIN, wrongPassword));
		HttpResponse<String> right = api.send(textPlainLogin(FOREIGN_ORIGIN, rightPassword));

		for (HttpResponse<String> response : List.of(wrong, right)) {
			assertEquals(403, response.statusCode());
			assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
			assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "no session opened");
			assertFalse(response.body().contains("accessToken"));
		}
		assertEquals(0, failedLoginAttempts(user), "the login use case did not run");
		assertEquals(0, refreshTokenCount(user), "no refresh token stored");
	}

	/** Spec §3.1, §3.4: a form-encoded body is not JSON, 415 MALFORMED_REQUEST, and nothing runs. */
	@Test
	void formEncodedLogin_answers415MalformedRequest() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = api.send(api.request(LOGIN)
				.header("Origin", ORIGIN)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(
						"username=" + user.username() + "&password=Wrong-password-1")));

		assertEquals(415, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("MALFORMED_REQUEST", body.path("code").asString());
		assertFalse(body.path("message").asString().isBlank(), "message");
		assertFalse(body.path("message").asString().contains(user.username()), "never echoes a submitted value");
		assertTrue(body.path("errors").isArray() && body.path("errors").isEmpty(), "errors []");
		assertTrue(body.has("correlationId"), "correlationId");
		assertCors(response);
		assertEquals(0, failedLoginAttempts(user), "the login use case did not run");
	}

	// ==================== unchanged public API ====================

	/** Spec §3.7: the public catalog of /list-with-us keeps its CORS headers. */
	@Test
	void publicProvinces_fromAllowedOrigin_stillCarriesCors() throws Exception {
		HttpResponse<String> response = api.send(api.request("/api/v1/public/provinces").header("Origin", ORIGIN).GET());

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		assertCors(response);
	}

	// ==================== Helpers ====================

	private HttpRequest.Builder preflight(String path, String origin) {
		return api.request(path)
				.header("Origin", origin)
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization, content-type")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
	}

	private HttpRequest.Builder textPlainLogin(String origin, String body) {
		return api.request(LOGIN)
				.header("Origin", origin)
				.header("Content-Type", "text/plain")
				.POST(HttpRequest.BodyPublishers.ofString(body));
	}

	private static void assertCors(HttpResponse<String> response) {
		assertEquals(ORIGIN, header(response, "Access-Control-Allow-Origin"));
		assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
	}

	private static String header(HttpResponse<String> response, String name) {
		return response.headers().firstValue(name).orElse(null);
	}

	private static List<String> lowerCaseList(String header) {
		assertNotNull(header);
		return Arrays.stream(header.split(",")).map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
	}

	private int failedLoginAttempts(Created user) {
		Integer attempts = jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class,
				user.userId());
		return attempts == null ? -1 : attempts;
	}

	private int refreshTokenCount(Created user) {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class,
				user.userId());
		return count == null ? -1 : count;
	}
}
