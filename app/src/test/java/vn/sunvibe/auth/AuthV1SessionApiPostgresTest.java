package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.REFRESH_COOKIE;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.cookieHeaderFor;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.cookieValue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.pathMatches;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * Login, refresh and logout of the browser session end to end on PostgreSQL (auth-browser-session spec §3.1 rows 1-3,
 * §3.5, §3.6, §3.9; W10-1, W10-2, W10-4..W10-8, W10-13, W10-16). The rate limit is off. Refresh and logout send the
 * allowed {@code Origin} unless the test is about it.
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
class AuthV1SessionApiPostgresTest {

	private static final String LOGIN = "/api/v1/auth/login";
	private static final String REFRESH = "/api/v1/auth/session/refresh";
	private static final String LOGOUT = "/api/v1/auth/session/logout";
	/** The one INVALID_CREDENTIALS message: a wrong password and an account that may not sign in look the same. */
	private static final String INVALID_CREDENTIALS_MESSAGE = "Phone number or password is incorrect.";

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

	// ==================== login ====================

	/** W10-1, W10-4, W10-16: the session body, and the refresh token only in the narrow cookie. */
	@Test
	void login_rightPassword_answersSessionBodyAndSetsTheNarrowCookie() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");

		HttpResponse<String> response = api.post(LOGIN, null, null, null,
				api.loginBody(user.username(), user.password()));

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(List.of("accessToken", "expiresIn", "tokenType", "user"),
				body.propertyNames().stream().sorted().toList());
		assertEquals("Bearer", body.path("tokenType").asString());
		assertFalse(body.path("accessToken").asString().isBlank());
		assertEquals(900, body.path("expiresIn").asInt());
		assertEquals(user.userId(), body.path("user").path("userId").asString());
		assertEquals(user.username(), body.path("user").path("username").asString());
		assertEquals(List.of("CUSTOMER", "TENANT"), strings(body.path("user").path("roles")));

		List<String> setCookies = response.headers().allValues("Set-Cookie");
		List<String> refreshCookies = setCookies.stream().filter(c -> c.startsWith(REFRESH_COOKIE + "=")).toList();
		assertEquals(1, refreshCookies.size(), "one refresh cookie");
		String cookie = refreshCookies.get(0);
		assertTrue(cookie.contains("; Path=/api/v1/auth/session"), "narrow path");
		assertTrue(cookie.contains("; Max-Age=604800"), "seven days");
		assertTrue(cookie.contains("; Secure"), "Secure");
		assertTrue(cookie.contains("; HttpOnly"), "HttpOnly");
		assertTrue(cookie.contains("; SameSite=Strict"), "SameSite=Strict");
		assertFalse(cookie.contains("Domain="), "host-only");
		assertTrue(setCookies.stream().noneMatch(c -> c.startsWith("cookie-jwt")), "no legacy cookie");
		String value = cookieValue(setCookies, REFRESH_COOKIE);
		assertFalse(value.isBlank());
		assertFalse(response.body().contains(value), "the body never carries the refresh token");
	}

	/**
	 * W10-5 (spec §10.1): with RFC 6265 path matching, the browser sends the refresh cookie to refresh and logout only,
	 * never to login, {@code /api/v1/me} or a path that merely starts with the same letters.
	 */
	@Test
	void login_refreshCookiePath_reachesOnlyRefreshAndLogout() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		HttpResponse<String> login = api.post(LOGIN, null, null, null,
				api.loginBody(user.username(), user.password()));
		assertEquals(200, login.statusCode());
		List<String> setCookies = login.headers().allValues("Set-Cookie");
		String cookie = setCookies.stream().filter(c -> c.startsWith(REFRESH_COOKIE + "=")).findFirst().orElseThrow();
		String path = AuthV1ApiTestSupport.attribute(cookie, "Path");

		assertTrue(pathMatches(path, REFRESH), "sent to refresh");
		assertTrue(pathMatches(path, LOGOUT), "sent to logout");
		assertFalse(pathMatches(path, LOGIN), "not sent to login");
		assertFalse(pathMatches(path, "/api/v1/me"), "not sent to /api/v1/me");
		assertFalse(pathMatches(path, "/api/v1/me/sessions/logout-all"), "not sent to logout-all");
		assertFalse(pathMatches(path, "/api/v1/auth/sessionX"), "not sent to a look-alike path");
		assertFalse(pathMatches(path, "/api/auth/refresh-token"), "not sent to the legacy refresh");
		assertNull(cookieHeaderFor(setCookies, LOGIN));
		assertNull(cookieHeaderFor(setCookies, "/api/v1/me"));

		// The cookie the browser would send to refresh is the one that works there
		String sent = cookieHeaderFor(setCookies, REFRESH);
		assertEquals(REFRESH_COOKIE + "=" + cookieValue(setCookies, REFRESH_COOKIE), sent);
		assertEquals(200, api.post(REFRESH, ORIGIN, sent, null, null).statusCode());
	}

	@Test
	void login_wrongPassword_answers401InvalidCredentials() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = api.post(LOGIN, null, null, null,
				api.loginBody(user.username(), "Wrong-password-1"));

		assertError(response, 401, "INVALID_CREDENTIALS");
		assertEquals(INVALID_CREDENTIALS_MESSAGE, api.body(response).path("message").asString());
		assertNoRefreshCookie(response);
	}

	/**
	 * auth-browser-session S-21: an account that may not sign in (LOCK, TERMINATED, DELETED) gets the same answer as
	 * a wrong password, even with the right password.
	 */
	@ParameterizedTest
	@EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED", "DELETED"})
	void login_accountThatMayNotSignIn_answers401InvalidCredentials(UserStatus status) throws Exception {
		Created user = api.createUser(status, "CUSTOMER");

		HttpResponse<String> response = api.post(LOGIN, null, null, null,
				api.loginBody(user.username(), user.password()));

		assertError(response, 401, "INVALID_CREDENTIALS");
		assertEquals(INVALID_CREDENTIALS_MESSAGE, api.body(response).path("message").asString());
		assertNoRefreshCookie(response);
	}

	@Test
	void login_fifthWrongPassword_answers423AccountLockedWithRetryAfter() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");
		String wrong = api.loginBody(user.username(), "Wrong-password-1");
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertEquals(401, api.post(LOGIN, null, null, null, wrong).statusCode(), "attempt " + attempt);
		}

		HttpResponse<String> response = api.post(LOGIN, null, null, null, wrong);

		assertError(response, 423, "ACCOUNT_LOCKED");
		long retryAfter = Long.parseLong(response.headers().firstValue("Retry-After").orElseThrow());
		assertTrue(retryAfter > 0, "Retry-After " + retryAfter);
	}

	// ==================== refresh ====================

	/** W10-7 */
	@Test
	void refresh_cookieAndAllowedOrigin_rotates() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER"));

		HttpResponse<String> response = api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null);

		assertEquals(200, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals("Bearer", body.path("tokenType").asString());
		assertEquals(900, body.path("expiresIn").asInt());
		assertEquals(List.of("CUSTOMER", "TENANT"), strings(body.path("user").path("roles")));
		String rotated = cookieValue(response.headers().allValues("Set-Cookie"), REFRESH_COOKIE);
		assertNotNull(rotated);
		assertFalse(rotated.isBlank());
		assertNotEquals(session.refreshCookie(), REFRESH_COOKIE + "=" + rotated, "a new refresh token");
		assertFalse(response.body().contains(rotated), "the body never carries the refresh token");
		assertTrue(response.headers().allValues("Set-Cookie").stream().noneMatch(c -> c.startsWith("cookie-jwt")));
	}

	/** W10-6: refused before the use case, so the token is neither rotated nor revoked. */
	@Test
	void refresh_withoutOrigin_answers403OriginNotAllowedAndKeepsTheToken() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(REFRESH, null, session.refreshCookie(), null, null);

		assertError(response, 403, "ORIGIN_NOT_ALLOWED");
		assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "no Set-Cookie on a refused request");
		assertEquals(200, api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null).statusCode(),
				"the token still refreshes");
	}

	/** W10-6: CORS in the chain refuses a present, not allowed Origin before any controller runs. */
	@Test
	void refresh_foreignOrigin_isRefusedByCorsAndKeepsTheToken() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(REFRESH, "https://evil.sunvibe.vn", session.refreshCookie(), null,
				null);

		assertEquals(403, response.statusCode());
		assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
		assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "no Set-Cookie on a refused request");
		assertEquals(200, api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null).statusCode(),
				"the token still refreshes");
	}

	/** W10-7: two tabs refresh at once with the same cookie; the second is inside the reuse grace. */
	@Test
	void refresh_twoParallelCallsWithOneCookie_bothSucceed() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(
					() -> refreshQuietly(session.refreshCookie()), pool);
			CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(
					() -> refreshQuietly(session.refreshCookie()), pool);

			assertEquals(200, first.join().statusCode());
			assertEquals(200, second.join().statusCode());
		}
	}

	/**
	 * auth-browser-session S-25: a sibling host planted a second cookie carrying its own valid session. Both values
	 * would refresh on their own, so the 401 can only come from the duplicate check, which runs before the use case:
	 * neither token is rotated or revoked, and each still works alone. Both header orders.
	 */
	@Test
	void refresh_cookieSentTwice_answers401AndRotatesNothing() throws Exception {
		Session planted = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));
		Session own = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> plantedFirst = api.post(REFRESH, ORIGIN,
				planted.refreshCookie() + "; " + own.refreshCookie(), null, null);
		HttpResponse<String> ownFirst = api.post(REFRESH, ORIGIN,
				own.refreshCookie() + "; " + planted.refreshCookie(), null, null);

		assertError(plantedFirst, 401, "SESSION_EXPIRED");
		assertClearsTheCookie(plantedFirst);
		assertError(ownFirst, 401, "SESSION_EXPIRED");
		assertClearsTheCookie(ownFirst);
		assertEquals(200, api.post(REFRESH, ORIGIN, planted.refreshCookie(), null, null).statusCode(),
				"the planted token was not rotated or revoked");
		assertEquals(200, api.post(REFRESH, ORIGIN, own.refreshCookie(), null, null).statusCode(),
				"the user's token was not rotated or revoked");
	}

	/**
	 * spec §10.1: a used token presented again after the 30 s reuse grace is taken as stolen; the session ends and the
	 * cookie is cleared. The grace is passed by moving the token's recorded use back in time, not the clock.
	 */
	@Test
	void refresh_reuseAfterGrace_answers401SessionExpiredAndClears() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));
		String oldToken = cookieValue(List.of(session.refreshCookie()), REFRESH_COOKIE);
		HttpResponse<String> rotated = api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null);
		assertEquals(200, rotated.statusCode());
		String newCookie = REFRESH_COOKIE + "="
				+ cookieValue(rotated.headers().allValues("Set-Cookie"), REFRESH_COOKIE);
		assertEquals(1, jdbc.update(
				"UPDATE refresh_tokens SET used_at = used_at - INTERVAL '5 minutes' WHERE token_hash = ?",
				RefreshTokenSecrets.hash(oldToken)));

		HttpResponse<String> reuse = api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null);

		assertError(reuse, 401, "SESSION_EXPIRED");
		assertClearsTheCookie(reuse);
		assertEquals(401, api.post(REFRESH, ORIGIN, newCookie, null, null).statusCode(),
				"reuse ends the whole session, the rotated token included");
	}

	@Test
	void refresh_noCookie_answers401SessionExpiredAndClears() throws Exception {
		HttpResponse<String> response = api.post(REFRESH, ORIGIN, null, null, null);

		assertError(response, 401, "SESSION_EXPIRED");
		assertClearsTheCookie(response);
	}

	/** W10-13, auth-browser-session BR-8: a session body is never cached. */
	@Test
	void refresh_answer_hasNoStore() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null);

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"),
				"Cache-Control " + response.headers().firstValue("Cache-Control").orElse(""));
	}

	// ==================== logout ====================

	/** W10-8 */
	@Test
	void logout_withCookie_answers204ClearsAndEndsTheSession() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(LOGOUT, ORIGIN, session.refreshCookie(), null, null);

		assertEquals(204, response.statusCode());
		assertClearsTheCookie(response);
		assertEquals(401, api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null).statusCode(),
				"the session has ended");
	}

	@Test
	void logout_noCookie_answers204() throws Exception {
		HttpResponse<String> response = api.post(LOGOUT, ORIGIN, null, null, null);

		assertEquals(204, response.statusCode());
		assertClearsTheCookie(response);
	}

	@Test
	void logout_withoutOrigin_answers403AndKeepsTheSession() throws Exception {
		Session session = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(LOGOUT, null, session.refreshCookie(), null, null);

		assertError(response, 403, "ORIGIN_NOT_ALLOWED");
		assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "no Set-Cookie on a refused request");
		assertEquals(200, api.post(REFRESH, ORIGIN, session.refreshCookie(), null, null).statusCode(),
				"the session is still alive");
	}

	/** auth-browser-session S-25: signing out with a planted cookie ends both sessions. */
	@Test
	void logout_cookieSentTwice_endsBothSessions() throws Exception {
		Session planted = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));
		Session own = api.logIn(api.createUser(UserStatus.ACTIVE, "CUSTOMER"));

		HttpResponse<String> response = api.post(LOGOUT, ORIGIN,
				planted.refreshCookie() + "; " + own.refreshCookie(), null, null);

		assertEquals(204, response.statusCode());
		assertClearsTheCookie(response);
		assertEquals(401, api.post(REFRESH, ORIGIN, planted.refreshCookie(), null, null).statusCode(),
				"the planted session has ended");
		assertEquals(401, api.post(REFRESH, ORIGIN, own.refreshCookie(), null, null).statusCode(),
				"the user's session has ended");
	}

	// ==================== legacy ====================

	/** W10-1: the old FE keeps working; the legacy body is unchanged. */
	@Test
	void legacyLogin_wrongPassword_keepsTheOldBody() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "CUSTOMER");

		HttpResponse<String> response = api.post("/api/auth/login", null, null, null,
				api.loginBody(user.username(), "Wrong-password-1"));

		assertEquals(401, response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(401, body.path("code").asInt(), response.body());
		assertTrue(body.has("errorTime"), response.body());
	}

	// ==================== Helpers ====================

	private HttpResponse<String> refreshQuietly(String cookie) {
		try {
			return api.post(REFRESH, ORIGIN, cookie, null, null);
		} catch (Exception e) {
			throw new IllegalStateException("refresh failed", e);
		}
	}

	private void assertError(HttpResponse<String> response, int status, String code) {
		assertEquals(status, response.statusCode(), "status " + response.statusCode());
		JsonNode body = api.body(response);
		assertEquals(code, body.path("code").asString(), "code");
		assertTrue(body.path("errors").isArray() && body.path("errors").isEmpty(), "errors []");
		assertFalse(body.path("message").asString().isBlank(), "message");
		assertTrue(body.has("correlationId"), "correlationId");
	}

	private static void assertClearsTheCookie(HttpResponse<String> response) {
		List<String> setCookies = response.headers().allValues("Set-Cookie");
		String cleared = setCookies.stream().filter(c -> c.startsWith(REFRESH_COOKIE + "=")).findFirst().orElse(null);
		assertNotNull(cleared, "a clearing Set-Cookie");
		assertEquals("", cookieValue(setCookies, REFRESH_COOKIE));
		assertTrue(cleared.contains("; Path=/api/v1/auth/session"), "same path");
		assertTrue(cleared.contains("; Max-Age=0"), "expired");
	}

	private static void assertNoRefreshCookie(HttpResponse<String> response) {
		assertNull(cookieValue(response.headers().allValues("Set-Cookie"), REFRESH_COOKIE));
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asString()));
		return values;
	}
}
