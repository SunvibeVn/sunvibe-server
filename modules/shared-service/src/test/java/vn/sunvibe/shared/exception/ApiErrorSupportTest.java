package vn.sunvibe.shared.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.shared.dto.response.error.ValidationError;

/** The error helpers the module exception handlers share (clean-up spec S-8, ADR-004 "errors"). */
class ApiErrorSupportTest {

    /** The depth the cause walk reads, the exception itself included. */
    private static final int CAUSE_DEPTH = 20;
    /** How many exceptions the chain walk reads before it assumes the worst. */
    private static final int EXCEPTIONS_CHECKED = 50;

    @Test
    void findCause_stopsAtTheDepthLimit() {
        IllegalStateException deepest = new IllegalStateException("target");

        assertSame(deepest, ApiErrorSupport.findCause(wrapped(deepest, CAUSE_DEPTH - 1), IllegalStateException.class));
        assertNull(ApiErrorSupport.findCause(wrapped(deepest, CAUSE_DEPTH), IllegalStateException.class));
    }

    @Test
    void findCause_noMatch_isNull() {
        assertNull(ApiErrorSupport.findCause(new RuntimeException(new IllegalArgumentException()),
                IllegalStateException.class));
    }

    @Test
    void anyInChain_matchInASuppressedException_isFound() {
        RuntimeException cause = new RuntimeException("cause");
        cause.addSuppressed(new IllegalStateException("suppressed while closing"));
        RuntimeException top = new RuntimeException("top", cause);

        assertTrue(ApiErrorSupport.anyInChain(top, IllegalStateException.class::isInstance));
    }

    @Test
    void anyInChain_matchInACause_isFound() {
        RuntimeException top = new RuntimeException("top", new IllegalStateException("cause"));

        assertTrue(ApiErrorSupport.anyInChain(top, IllegalStateException.class::isInstance));
    }

    @Test
    void anyInChain_noMatch_isFalse() {
        RuntimeException cause = new RuntimeException("cause");
        cause.addSuppressed(new IllegalArgumentException("suppressed"));
        RuntimeException top = new RuntimeException("top", cause);

        assertFalse(ApiErrorSupport.anyInChain(top, IllegalStateException.class::isInstance));
    }

    /** A cycle is walked once: the walk ends without a match and without hitting the limit. */
    @Test
    void anyInChain_selfSuppressedCycle_isFalse() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.addSuppressed(second);

        assertFalse(ApiErrorSupport.anyInChain(second, IllegalStateException.class::isInstance));
    }

    @Test
    void anyInChain_tooManyExceptions_assumesTrue() {
        RuntimeException top = new RuntimeException("top");
        for (int i = 0; i < EXCEPTIONS_CHECKED + 10; i++) {
            top.addSuppressed(new IllegalArgumentException("suppressed " + i));
        }

        assertTrue(ApiErrorSupport.anyInChain(top, IllegalStateException.class::isInstance),
                "a chain too long to check is assumed to match");
    }

    @Test
    void jsonPath_indexesAndNames() {
        JacksonException error = assertThrows(JacksonException.class, () -> JsonMapper.builder().build()
                .readValue("{\"spaces\":[{\"code\":1},{\"code\":\"x\"}]}", Form.class));

        assertEquals("spaces.1.code", ApiErrorSupport.jsonPath(error));
    }

    @Test
    void parameterName_headerQueryPath() throws Exception {
        Method method = Endpoints.class.getDeclaredMethod("handle", String.class, String.class, String.class,
                String.class, String.class, String.class);

        assertEquals("X-Upload-Key", ApiErrorSupport.parameterName(new MethodParameter(method, 0)));
        assertEquals("knownVersion", ApiErrorSupport.parameterName(new MethodParameter(method, 1)));
        assertEquals("page", ApiErrorSupport.parameterName(new MethodParameter(method, 2)));
        assertEquals("leadId", ApiErrorSupport.parameterName(new MethodParameter(method, 3)));
        assertEquals("mediaId", ApiErrorSupport.parameterName(new MethodParameter(method, 4)));
        assertEquals("request", ApiErrorSupport.parameterName(new MethodParameter(method, 5)),
                "the modules compile without -parameters, so an unnamed parameter has no name to give");
    }

    @Test
    void sortedByField_keepsTheFirstErrorOfEachFieldInFieldOrder() {
        List<ValidationError> sorted = ApiErrorSupport.sortedByField(List.of(
                ValidationError.of("title", "first title error"),
                ValidationError.of("area", "area error"),
                ValidationError.of("title", "second title error")));

        assertEquals(List.of(ValidationError.of("area", "area error"),
                ValidationError.of("title", "first title error")), sorted);
    }

    private static RuntimeException wrapped(Throwable deepest, int levels) {
        Throwable current = deepest;
        for (int i = 0; i < levels; i++) {
            current = new RuntimeException("level " + i, current);
        }
        return (RuntimeException) current;
    }

    record Form(List<Space> spaces) {
    }

    record Space(int code) {
    }

    @SuppressWarnings("unused")
    static class Endpoints {

        void handle(@RequestHeader("X-Upload-Key") String uploadKey,
                    @RequestParam(value = "knownVersion", required = false) String knownVersion,
                    @RequestParam(name = "page") String page,
                    @PathVariable("leadId") String leadId,
                    @PathVariable(name = "mediaId") String mediaId,
                    String body) {
        }
    }
}
