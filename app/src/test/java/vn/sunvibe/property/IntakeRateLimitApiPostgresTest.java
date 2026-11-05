package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;

/**
 * The limits of property-create intake spec 7.4 on the real paths, through the real filter chain. The limiter runs before Spring
 * Security, so a call with no token counts: each answers 401 until the limit. Needs Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"app.security.rate-limit.enabled=true",
		"app.property.intake.enabled=true",
		"app.property.direct-create.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class IntakeRateLimitApiPostgresTest {

	private static final String CONFIRMATIONS = "/api/v1/me/property-confirmations/";
	private static final String REQUEST = "/api/v1/staff/owner-leads/%s/inventory-intake/owner-confirmation";

	private final HttpClient http = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int port;

	private int post(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	private int get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(30)).GET().build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

	/** Every verb and every id share the 10 of one address; the list is not limited. */
	@Test
	void rateLimit_realPaths() throws Exception {
		String[] verbs = { "details", "confirm", "decline", "check", "withdrawal" };
		for (int i = 0; i < 10; i++) {
			assertEquals(401, post(CONFIRMATIONS + "request-" + i + "/" + verbs[i % verbs.length]), "try " + i);
		}
		assertEquals(429, post(CONFIRMATIONS + "another/details"));
		assertEquals(401, get("/api/v1/me/property-confirmations"));

		for (int i = 0; i < 30; i++) {
			assertEquals(401, post(REQUEST.formatted("lead-" + i)), "try " + i);
		}
		assertEquals(429, post(REQUEST.formatted("another")));
	}
}
