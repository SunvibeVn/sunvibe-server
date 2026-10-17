package vn.sunvibe.config.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import vn.sunvibe.shared.logging.CorrelationId;

/** Correlation id per request (ADR-010; W9-16). */
class CorrelationIdFilterTest {

    private static final String ULID_PATTERN = "[0-9A-HJKMNP-TV-Z]{26}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> currentInChain = new AtomicReference<>();
    private final AtomicReference<String> mdcInChain = new AtomicReference<>();
    private final AtomicReference<String> headerInChain = new AtomicReference<>();

    /** Records what a controller would see: the id, the MDC and the response header, all set before the chain runs. */
    private final FilterChain recordingChain = (req, res) -> {
        currentInChain.set(CorrelationId.current());
        mdcInChain.set(MDC.get(CorrelationId.MDC_KEY));
        headerInChain.set(((MockHttpServletResponse) res).getHeader(CorrelationId.HEADER));
    };

    @Test
    void filter_noHeader_generatesUlidAndEchoesItInTheResponse() throws Exception {
        filter.doFilter(request, response, recordingChain);

        String id = response.getHeader(CorrelationId.HEADER);
        assertNotNull(id);
        assertTrue(id.matches(ULID_PATTERN), id);
        assertEquals(id, currentInChain.get());
        assertEquals(id, mdcInChain.get());
        assertEquals(id, headerInChain.get());
    }

    @Test
    void filter_validIncomingHeader_isKept() throws Exception {
        request.addHeader(CorrelationId.HEADER, "client-req-12345");

        filter.doFilter(request, response, recordingChain);

        assertEquals("client-req-12345", response.getHeader(CorrelationId.HEADER));
        assertEquals("client-req-12345", currentInChain.get());
    }

    @Test
    void filter_tooShortHeader_isReplaced() throws Exception {
        request.addHeader(CorrelationId.HEADER, "short");

        filter.doFilter(request, response, recordingChain);

        assertReplaced("short");
    }

    @Test
    void filter_headerWithSpacesOrNewline_isReplaced() throws Exception {
        request.addHeader(CorrelationId.HEADER, "has space in it\nsecond");

        filter.doFilter(request, response, recordingChain);

        assertReplaced("has space in it\nsecond");
    }

    @Test
    void filter_65Chars_isReplaced() throws Exception {
        String tooLong = "a".repeat(65);
        request.addHeader(CorrelationId.HEADER, tooLong);

        filter.doFilter(request, response, recordingChain);

        assertReplaced(tooLong);
    }

    @Test
    void filter_64Chars_isKept() throws Exception {
        String longest = "a".repeat(64);
        request.addHeader(CorrelationId.HEADER, longest);

        filter.doFilter(request, response, recordingChain);

        assertEquals(longest, response.getHeader(CorrelationId.HEADER));
    }

    @Test
    void filter_afterTheRequest_clearsTheMdc() throws Exception {
        filter.doFilter(request, response, new MockFilterChain());

        assertNull(MDC.get(CorrelationId.MDC_KEY));
        assertNull(CorrelationId.current());
    }

    @Test
    void filter_chainThrows_stillClearsTheMdc() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, failing));

        assertNull(MDC.get(CorrelationId.MDC_KEY));
        assertNotNull(response.getHeader(CorrelationId.HEADER));
    }

    @Test
    void isValid_boundaries_follow8To64LettersDigitsAndDash() {
        assertFalse(CorrelationId.isValid(null));
        assertFalse(CorrelationId.isValid("1234567"));
        assertTrue(CorrelationId.isValid("12345678"));
        assertTrue(CorrelationId.isValid("a-B-1-2-3-4"));
        assertFalse(CorrelationId.isValid("under_score1"));
    }

    private void assertReplaced(String rejected) {
        String id = response.getHeader(CorrelationId.HEADER);
        assertNotEquals(rejected, id);
        assertTrue(id.matches(ULID_PATTERN), id);
        assertEquals(id, currentInChain.get());
    }
}
