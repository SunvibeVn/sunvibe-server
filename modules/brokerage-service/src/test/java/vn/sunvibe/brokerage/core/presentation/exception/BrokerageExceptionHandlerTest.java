package vn.sunvibe.brokerage.core.presentation.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error mapping of the brokerage agreement eligibility endpoint (E05-S1 spec 4, ADR-010; plan decision E05S1-P5): its
 * 404, the rethrown Spring Security errors, a framework 4xx with its status and headers, and a 500 that keeps the
 * exception's message out of the answer and the log.
 */
class BrokerageExceptionHandlerTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";

    private final BrokerageExceptionHandler handler = new BrokerageExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(BrokerageExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    void handleNotFound_leadNotVisible_returns404WithCodeEmptyErrorsAndCorrelationId() {
        ResponseEntity<ErrorResponse<ValidationError>> response = handler.handleNotFound(new BrokerageNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCode.NOT_FOUND, response.getBody().code());
        assertEquals("The resource was not found.", response.getBody().message());
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
    }

    @Test
    void handleUnexpected_accessDenied_isRethrownForSpringSecurity() {
        AccessDeniedException denied = new AccessDeniedException("denied");

        assertSame(denied, assertThrows(AccessDeniedException.class, () -> handler.handleUnexpected(denied)));
    }

    @Test
    void handleUnexpected_authenticationFailure_isRethrownForSpringSecurity() {
        BadCredentialsException failure = new BadCredentialsException("bad");

        assertSame(failure, assertThrows(BadCredentialsException.class, () -> handler.handleUnexpected(failure)));
    }

    @Test
    void handleUnexpected_methodNotAllowed_keepsItsStatusAndAllowHeader() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handler.handleUnexpected(new HttpRequestMethodNotSupportedException("POST", List.of("GET")));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(Set.of(HttpMethod.GET), response.getHeaders().getAllow());
        assertEquals(ErrorCode.MALFORMED_REQUEST, response.getBody().code());
        assertEquals("The request cannot be processed.", response.getBody().message());
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
        assertTrue(logs.list.isEmpty(), "a client error is not logged as a server error");
    }

    @Test
    void handleUnexpected_anyException_returns500InternalErrorWithoutItsMessage() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handler.handleUnexpected(new IllegalStateException("secret detail 0900000001"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR, response.getBody().code());
        assertEquals("Unexpected server error.", response.getBody().message());
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        String line = event.getFormattedMessage();
        assertTrue(line.contains("IllegalStateException"), line);
        assertTrue(line.contains(CORRELATION_ID), line);
        assertFalse(line.contains("secret"), line);
        assertNull(event.getThrowableProxy(), "no stack trace: it can carry the message of a SQL cause");
    }
}
