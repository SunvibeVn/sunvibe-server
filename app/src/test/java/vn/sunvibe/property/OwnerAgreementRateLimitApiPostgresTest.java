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
 * The limit of brokerage-agreement E05-S5 spec S5-D4 (OC-3 rule part) on the real paths, through the real filter chain:
 * the owner's two command paths share 10 POSTs per address in 15 minutes; the owner's read is not limited. The limiter
 * runs before Spring Security, so a call with no token counts and answers 401 until the limit. The annotations of
 * {@link IntakeRateLimitApiPostgresTest}, so both share one context (limiter on); the buckets differ. Needs Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"app.security.rate-limit.enabled=true",
		"app.property.intake.enabled=true",
		"app.property.direct-create.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class OwnerAgreementRateLimitApiPostgresTest {

	private static final String ME = "/api/v1/me/brokerage-agreements";

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

	/** Both command paths share the 10 of one address; the eleventh answers 429; the list is not limited. */
	@Test
	void rateLimit_ownerCommandPaths() throws Exception {
		for (int i = 0; i < 10; i++) {
			String path = i % 2 == 0 ? ME + "/confirmations" : ME + "/01JAGREEMENT0000000000000" + i + "/rejection";
			assertEquals(401, post(path), "try " + i);
		}
		assertEquals(429, post(ME + "/confirmations"));
		assertEquals(429, post(ME + "/01JAGREEMENT00000000000099/rejection"));
		for (int i = 0; i < 12; i++) {
			assertEquals(401, get(ME), "read " + i);
		}
	}
}
