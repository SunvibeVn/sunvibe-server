package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * The rate rule of the anonymous public read over HTTP (listing-publish E06-S7 spec E06S7-D8; criterion PB-14): GETs sent
 * back to back from one address, the switch on; the first 60 answer anything but 429, and a 429 {@code RATE_LIMITED}
 * with a {@code Retry-After} of at least 1 arrives within the next 10 (the bucket refills one token a second, so the
 * bound holds while the first 60 take under 10 seconds: testing.md, a generous bound). A context of its own, so no other
 * class spends this address's tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.security.rate-limit.enabled=true")
@ActiveProfiles("test")
class ListingPublicRateLimitApiTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Value("${local.server.port}")
	private int port;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	void get_backToBack_sixtyPassThenRateLimited() throws Exception {
		for (int i = 0; i < 60; i++) {
			assertNotEquals(429, get().statusCode(), "try " + i);
		}

		HttpResponse<String> limited = null;
		for (int i = 0; i < 10 && limited == null; i++) {
			HttpResponse<String> response = get();
			if (response.statusCode() == 429) {
				limited = response;
			}
		}

		assertNotNull(limited, "no 429 within ten GETs after the first sixty");
		assertEquals("RATE_LIMITED", JSON.readTree(limited.body()).path("code").asString());
		long retryAfter = Long.parseLong(limited.headers().firstValue("Retry-After").orElse("0"));
		assertTrue(retryAfter >= 1, "Retry-After " + retryAfter);
	}

	private HttpResponse<String> get() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(
						URI.create("http://localhost:" + port + "/api/v1/public/listings/TD-2345-6789"))
				.timeout(Duration.ofSeconds(30))
				.GET()
				.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
