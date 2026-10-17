package vn.sunvibe.property.core.presentation.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;
import vn.sunvibe.property.core.domain.exception.ProvinceNotFoundException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Error mapping of the public catalog endpoints (acquisition-owner-intake spec 5.2, ADR-010; W9-14, W9-16). */
class AdministrativeAreaExceptionHandlerTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";

    private final AdministrativeAreaExceptionHandler handler = new AdministrativeAreaExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AdministrativeAreaExceptionHandler.class);
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
    void handleNotFound_unknownProvince_returns404WithCodeEmptyErrorsAndCorrelationId() {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handler.handleNotFound(new ProvinceNotFoundException("Province was not found."));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(ErrorCode.NOT_FOUND, response.getBody().code());
        assertEquals("Province was not found.", response.getBody().message());
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
    }

    @Test
    void handleUnexpected_anyException_returns500WithoutItsMessage() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handler.handleUnexpected(new IllegalStateException("secret detail 0900000001"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR, response.getBody().code());
        assertFalse(response.getBody().message().contains("secret"));
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
    }

    @Test
    void handleUnexpected_anyException_logsOnlyTheClassAndTheCorrelationId() throws Exception {
        handler.handleUnexpected(new IllegalStateException("secret detail 0900000001"));

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        String line = event.getFormattedMessage();
        assertTrue(line.contains("IllegalStateException"), line);
        assertTrue(line.contains(CORRELATION_ID), line);
        assertFalse(line.contains("secret"), line);
        assertNull(event.getThrowableProxy(), "no stack trace: it can carry the message of a SQL cause");
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

    /**
     * Spec 5.2 and 6.5 (same rules as the acquisition handler): another Spring MVC error keeps its own status and
     * headers, with the ADR-010 body {@code MALFORMED_REQUEST}, no field errors and the correlation id.
     */
    @Test
    void handleUnexpected_springWebError_keepsItsStatusWithMalformedRequestBody() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response =
                handler.handleUnexpected(new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "secret 0900"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(ErrorCode.MALFORMED_REQUEST, response.getBody().code());
        assertEquals("The request cannot be processed.", response.getBody().message());
        assertEquals(List.of(), response.getBody().errors());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
        assertTrue(logs.list.isEmpty(), "a client error is not logged as a server error");
    }

    @Test
    void handleUnexpected_unsupportedMediaType_keepsItsStatusAndAcceptHeader() throws Exception {
        HttpMediaTypeNotSupportedException unsupported = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse<ValidationError>> response = handler.handleUnexpected(unsupported);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(List.of(MediaType.APPLICATION_JSON), response.getHeaders().getAccept());
        assertEquals(ErrorCode.MALFORMED_REQUEST, response.getBody().code());
        assertEquals(List.of(), response.getBody().errors());
    }
}
