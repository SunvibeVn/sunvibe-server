package vn.sunvibe.auth.core.infrastructure.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorsConfigTest {

    private static final String ORIGIN = "https://app.example";

    private final CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(List.of(ORIGIN));

    @Test
    void corsConfigurationSource_exposedHeaders_listsTheThreeHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/provinces");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertNotNull(configuration);
        assertEquals(List.of("X-Correlation-Id", "Idempotent-Replayed", "Retry-After"), configuration.getExposedHeaders());
    }

    @Test
    void corsConfigurationSource_simpleRequest_responseExposesTheThreeHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/provinces");
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);

        assertEquals(ORIGIN, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals("X-Correlation-Id, Idempotent-Replayed, Retry-After",
                response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
    }
}
