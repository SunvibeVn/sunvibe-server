package vn.sunvibe.survey.core.presentation.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON bodies of create, removal, hold and release are capped at 16 KiB, counted while reading and before parsing
 * (survey-media spec A-11, the create order (0) of 5.4; SM-24; SV-053): a larger announced length is answered 413
 * here, a body without a length fails its read past the limit. Other paths are not filtered.
 */
class MediaJsonBodyLimitFilterTest {

    private static final String CREATE = "/api/v1/staff/survey-checklists/01JCHECK000000000000000001/media";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MediaJsonBodyLimitFilter filter = new MediaJsonBodyLimitFilter(JSON);

    @Test
    void bodyOver16KiB_413BeforeParsing() throws Exception {
        MockHttpServletRequest request = post(CREATE, "application/json", new byte[16 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        JsonNode body = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals("PAYLOAD_TOO_LARGE", body.path("code").asString());
        assertEquals(0, body.path("errors").size());
        assertNull(chain.getRequest(), "nothing is read or parsed");
    }

    @Test
    void exactly16KiB_passes() throws Exception {
        MockHttpServletRequest request = post(CREATE, "application/json", new byte[16 * 1024]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(16 * 1024, ((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes().length);
    }

    @Test
    void bodyWithoutLength_overLimit_failsWhileReading() throws Exception {
        MockHttpServletRequest request = chunked(CREATE, "application/json", new byte[17 * 1024]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        InputStream body = ((HttpServletRequest) chain.getRequest()).getInputStream();
        assertThrows(MediaBodyTooLargeException.class, body::readAllBytes);
    }

    @Test
    void bodyWithoutLength_notJson_415() throws Exception {
        MockHttpServletRequest request = chunked(CREATE, "application/x-www-form-urlencoded", new byte[10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(415, response.getStatus());
        assertEquals("MALFORMED_REQUEST", JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8))
                .path("code").asString());
        assertNull(chain.getRequest());
    }

    @Test
    void filteredPaths_createRemovalHoldRelease() {
        for (String path : List.of(CREATE, "/api/v1/staff/survey-media/01JMED1A000000000000000001/removal",
                "/api/v1/staff/survey-media/01JMED1A000000000000000001/hold",
                "/api/v1/staff/survey-media/01JMED1A000000000000000001/hold-release",
                "/api/v1/staff/survey-checklists/01JCHECK000000000000000001/media/01JMED1A000000000000000001/deselection")) {
            assertFalse(filter.shouldNotFilter(post(path, "application/json", new byte[0])), path);
        }
    }

    @Test
    void otherPaths_notFiltered() {
        for (String path : List.of("/api/v1/staff/survey-media/01JMED1A000000000000000001/content",
                "/api/v1/staff/survey-media/01JMED1A000000000000000001/content/full",
                "/api/v1/staff/survey-checklists/01JCHECK000000000000000001/identification",
                "/api/v1/staff/survey-checklists/01JCHECK000000000000000001",
                "/api/v1/staff/survey-checklists/01JCHECK000000000000000001/media/extra")) {
            assertTrue(filter.shouldNotFilter(post(path, "application/json", new byte[0])), path);
        }
    }

    /** A body sent without a length (chunked): the container reports -1. */
    private static MockHttpServletRequest chunked(String path, String contentType, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setRequestURI(path);
        request.setContentType(contentType);
        request.setContent(body);
        request.addHeader("Transfer-Encoding", "chunked");
        return request;
    }

    private static MockHttpServletRequest post(String path, String contentType, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setContentType(contentType);
        request.setContent(body);
        return request;
    }
}
