package vn.sunvibe.config.web;

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

import vn.sunvibe.shared.logging.CorrelationId;

/** The correlation id header on real HTTP responses, public GET without a token (ADR-010; W9-16). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CorrelationIdApiTest {

    private static final String PUBLIC_PATH = "/v3/api-docs";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void publicGet_noHeader_responseHasGeneratedCorrelationId() throws Exception {
        HttpResponse<String> response = get(null);

        String id = response.headers().firstValue(CorrelationId.HEADER).orElse(null);
        assertNotNull(id);
        assertTrue(CorrelationId.isValid(id), id);
    }

    @Test
    void publicGet_validHeader_isEchoed() throws Exception {
        HttpResponse<String> response = get("client-req-12345");

        assertEquals("client-req-12345", response.headers().firstValue(CorrelationId.HEADER).orElse(null));
    }

    @Test
    void publicGet_badHeader_isReplaced() throws Exception {
        HttpResponse<String> response = get("bad id");

        String id = response.headers().firstValue(CorrelationId.HEADER).orElse(null);
        assertNotNull(id);
        assertNotEquals("bad id", id);
        assertTrue(CorrelationId.isValid(id), id);
    }

    private HttpResponse<String> get(String correlationId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PUBLIC_PATH))
                .timeout(Duration.ofSeconds(30)).GET();
        if (correlationId != null) {
            builder.header(CorrelationId.HEADER, correlationId);
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response;
    }
}
