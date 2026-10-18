package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Session;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * W10-60 (auth-browser-session spec §3.12, BR-12, BR-5): the 16 legacy auth paths under {@code /api/auth},
 * {@code /api/users/command} and {@code /api/user/query} are no longer mapped, so an anonymous call answers the chain's
 * 401, and the access-token cookie {@code cookie-jwt} is no longer read. The rate limit is off.
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
class LegacyAuthPathsRemovedApiTest {

	private static final List<String> LEGACY_PREFIXES = List.of("/api/auth", "/api/users/command", "/api/user/query");

	/** The 16 legacy operations as they were mapped: method and path. */
	private static final List<List<String>> LEGACY_OPERATIONS = List.of(
			List.of("POST", "/api/auth/login"),
			List.of("POST", "/api/auth/refresh-token"),
			List.of("POST", "/api/auth/logout"),
			List.of("POST", "/api/auth/logout-all"),
			List.of("POST", "/api/auth/change-password"),
			List.of("POST", "/api/auth/forgot-password"),
			List.of("POST", "/api/auth/forgot-password/sms"),
			List.of("POST", "/api/auth/reset-password"),
			List.of("POST", "/api/auth/verification/phone/send"),
			List.of("POST", "/api/auth/verification/phone/confirm"),
			List.of("POST", "/api/auth/verification/email/send"),
			List.of("POST", "/api/auth/verification/email/confirm"),
			List.of("POST", "/api/auth/registration/phone/send"),
			List.of("POST", "/api/auth/registration/phone/verify"),
			List.of("POST", "/api/users/command/register"),
			List.of("GET", "/api/user/query/my-info"));

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	private AuthV1ApiTestSupport api;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
	}

	@AfterAll
	void tearDown() {
		api.close();
	}

	/** W10-60: a removed path is no longer public, so an anonymous call gets the chain's 401 (spec §3.12). */
	@Test
	void legacyPaths_anonymous_answer401() throws Exception {
		assertEquals(16, LEGACY_OPERATIONS.size());
		for (List<String> operation : LEGACY_OPERATIONS) {
			String method = operation.get(0);
			String path = operation.get(1);
			HttpRequest.Builder request = api.request(path)
					.header("Content-Type", "application/json")
					.method(method, "GET".equals(method)
							? HttpRequest.BodyPublishers.noBody()
							: HttpRequest.BodyPublishers.ofString("{}"));

			HttpResponse<String> response = api.send(request);

			assertEquals(401, response.statusCode(), method + " " + path + ": status " + response.statusCode());
			// The security chain's entry-point body (SecurityError), so no handler answering 401 can pass
			JsonNode body = api.body(response);
			assertEquals("Unauthorized", body.path("error").asString(), method + " " + path);
			assertFalse(body.path("message").asString().isBlank(), method + " " + path);
			assertEquals(path, body.path("path").asString(), method + " " + path);
		}
	}

	/** W10-60: no handler method is mapped under a legacy prefix. */
	@Test
	void legacyPaths_noHandlerIsMapped() {
		List<String> legacyPatterns = handlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream())
				.filter(pattern -> LEGACY_PREFIXES.stream()
						.anyMatch(prefix -> pattern.equals(prefix) || pattern.startsWith(prefix + "/")))
				.sorted()
				.toList();

		assertTrue(legacyPatterns.isEmpty(), "still mapped: " + legacyPatterns);
	}

	/** W10-60: {@code cookie-jwt} is no longer read; only {@code Authorization: Bearer} authenticates. */
	@Test
	void accessCookieOnly_isNotAccepted() throws Exception {
		Created user = api.createUser(UserStatus.ACTIVE, "TENANT", "CUSTOMER");
		Session session = api.logIn(user);
		assertEquals(200, api.get("/api/v1/me", session.accessToken()).statusCode(), "the token itself is valid");

		HttpResponse<String> response = api.send(api.request("/api/v1/me")
				.header("Cookie", "cookie-jwt=" + session.accessToken())
				.GET());

		assertEquals(401, response.statusCode(), "status " + response.statusCode());
	}
}
