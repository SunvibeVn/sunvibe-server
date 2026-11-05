package vn.sunvibe.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.auth.AuthV1ApiTestSupport.ORIGIN;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Created;
import vn.sunvibe.auth.AuthV1ApiTestSupport.Session;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * A pool far below the requests in flight (auth-email-access S-47, spec 5.4): no flow may hold a connection while it waits
 * for a second one, or for SMTP, so eight requests on two connections finish without a pool timeout.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"spring.datasource.hikari.maximum-pool-size=2",
			"spring.datasource.hikari.minimum-idle=1",
			"spring.datasource.hikari.connection-timeout=5000" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, CapturingMessagesConfig.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSmallPoolPostgresTest {

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
	private ExecutorService pool;

	@BeforeAll
	void setUp() {
		api = new AuthV1ApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		pool = Executors.newFixedThreadPool(8);
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		api.close();
	}

	private String json(Map<String, ?> body) throws Exception {
		return api.json().writeValueAsString(body);
	}

	private static String freshAddress() {
		return "pool-" + UUID.randomUUID() + "@example.com";
	}

	@Test
	void smallPool_eightMixedRequests_noConnectionTimeout() throws Exception {
		Created phoneUser = api.createUser(vn.sunvibe.auth.core.domain.model.user.enums.UserStatus.ACTIVE, "CUSTOMER");
		Created emailUser = api.createEmailUser("CUSTOMER");
		Created resetUser = api.createUser(vn.sunvibe.auth.core.domain.model.user.enums.UserStatus.ACTIVE, "CUSTOMER");
		Session session = api.logIn(phoneUser);
		HttpResponse<String> started = api.post("/api/v1/auth/password-reset/sms", null, null, null,
				json(Map.of("phoneNumber", resetUser.username())));
		assertEquals(200, started.statusCode());
		String resetBody = json(Map.of("resetToken", api.body(started).path("resetToken").asString(), "otpCode",
				messages.lastCode(resetUser.username()), "newPassword", "Sunvibe@2027", "confirmPassword", "Sunvibe@2027"));

		CountDownLatch start = new CountDownLatch(1);
		List<Callable<HttpResponse<String>>> calls = List.of(
				() -> api.post("/api/v1/auth/login", null, null, null, api.loginBody(phoneUser.username(), phoneUser.password())),
				() -> api.post("/api/v1/auth/login", null, null, null, json(Map.of("email", emailUser.username(), "password",
						"Wrong@Pass1"))),
				() -> api.post("/api/v1/auth/session/refresh", ORIGIN, session.refreshCookie(), null, null),
				() -> api.post("/api/v1/auth/password-reset/email", null, null, null, json(Map.of("email", emailUser.username()))),
				() -> api.post("/api/v1/auth/password-reset/complete", null, null, null, resetBody),
				() -> api.post("/api/v1/auth/registration/email/send-code", null, null, null, json(Map.of("email", freshAddress()))),
				() -> api.post("/api/v1/me/email/send-code", null, null, session.accessToken(), json(Map.of("newEmail", freshAddress(),
						"currentPassword", phoneUser.password()))),
				() -> api.get("/api/v1/me", session.accessToken()));
		assertEquals(8, calls.size());
		List<Future<HttpResponse<String>>> running = new ArrayList<>();
		for (Callable<HttpResponse<String>> call : calls) {
			running.add(pool.submit(() -> {
				start.await();
				return call.call();
			}));
		}
		start.countDown();

		for (Future<HttpResponse<String>> call : running) {
			int status = call.get(30, TimeUnit.SECONDS).statusCode();
			assertTrue(status < 500, "status " + status);
		}
	}
}
