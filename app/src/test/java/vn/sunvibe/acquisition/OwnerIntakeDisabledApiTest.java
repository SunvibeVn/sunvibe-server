package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.acquisition.core.presentation.controller.OwnerIntakeController;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * With the intake switch off (acquisition-owner-intake BR-15, spec C-7) the anonymous POST answers 404 through the
 * whole filter chain, even for a body that would fail validation, and a browser on an allowed origin can still read
 * the answer and its correlation id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.acquisition.owner-intake.enabled=false")
@ActiveProfiles("test")
class OwnerIntakeDisabledApiTest {

	private static final String ORIGIN = "http://localhost:3000";
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void post_switchOff_answers404WithTheErrorBody() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + OwnerIntakeController.PATH))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.header("Origin", ORIGIN)
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(404, response.statusCode());
		JsonNode body = JSON.readTree(response.body());
		assertEquals("NOT_FOUND", body.path("code").asString());
		assertEquals(0, body.path("errors").size());
		String correlationId = response.headers().firstValue(CorrelationId.HEADER).orElse("");
		assertEquals(correlationId, body.path("correlationId").asString());
		assertEquals(ORIGIN, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
		assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").orElse("")
				.contains(CorrelationId.HEADER));
	}
}
