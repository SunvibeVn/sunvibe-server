package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * With the public read's switch off (listing-publish E06-S7 spec E06S7-D7, D8; criterion PB-13) a GET of a well-formed
 * code answers the one 404 through the whole filter chain, with {@code Cache-Control: no-store} and the correlation id,
 * as {@code OwnerIntakeDisabledApiTest} holds the owner intake switch. A context of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.listing.public-read.enabled=false")
@ActiveProfiles("test")
class ListingPublicSwitchApiTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void get_switchOffWellFormedCode_theOneNotFound() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(
						URI.create("http://localhost:" + port + "/api/v1/public/listings/TD-2345-6789"))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(404, response.statusCode(), response.body());
		assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
		JsonNode body = JSON.readTree(response.body());
		assertEquals("NOT_FOUND", body.path("code").asString());
		assertEquals("The resource was not found.", body.path("message").asString());
		assertEquals(0, body.path("errors").size());
		assertEquals(response.headers().firstValue(CorrelationId.HEADER).orElse(""),
				body.path("correlationId").asString());
	}
}
