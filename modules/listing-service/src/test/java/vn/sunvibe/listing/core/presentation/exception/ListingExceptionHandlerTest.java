package vn.sunvibe.listing.core.presentation.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import vn.sunvibe.listing.core.application.exception.ListingNotFoundException;
import vn.sunvibe.listing.core.application.exception.ListingRefusedException;
import vn.sunvibe.listing.core.domain.exception.ListingCodeTakenException;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.NOT_STARTED;
import static vn.sunvibe.listing.core.domain.model.listing.AgreementReasonGroup.OWNER;

/**
 * The error bodies of the listing endpoints (listing-publish E06-S1 spec section 4, E06S1-P3; ADR-010): each refusal's
 * status, stable code, fixed message and field entries, never a value of the request or of a row.
 */
class ListingExceptionHandlerTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";

    private final ListingExceptionHandler handler = new ListingExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ListingExceptionHandler.class);
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
    void handle_refusals_codeStatusMessageAndEntries() {
        ResponseEntity<ErrorResponse<ValidationError>> notAllowed =
                handler.handleRefused(ListingRefusedException.notAllowed(List.of(NOT_STARTED, OWNER)));

        assertEquals(HttpStatus.CONFLICT, notAllowed.getStatusCode());
        assertEquals("LISTING_NOT_ALLOWED", notAllowed.getBody().code());
        assertEquals("A listing cannot be created for this unit now.", notAllowed.getBody().message());
        assertEquals(List.of(ValidationError.of("reasonGroups.NOT_STARTED", "This group stops the listing."),
                ValidationError.of("reasonGroups.OWNER", "This group stops the listing.")), notAllowed.getBody().errors());
        assertEquals(CORRELATION_ID, notAllowed.getBody().correlationId());

        ResponseEntity<ErrorResponse<ValidationError>> exists = handler.handleRefused(ListingRefusedException.exists());

        assertEquals(HttpStatus.CONFLICT, exists.getStatusCode());
        assertEquals("LISTING_EXISTS", exists.getBody().code());
        assertEquals("This agreement already has a listing.", exists.getBody().message());
        assertEquals(List.of(), exists.getBody().errors());
    }

    @Test
    void handle_notFoundCodeTakenConflict_codes() {
        ResponseEntity<ErrorResponse<ValidationError>> notFound = handler.handleNotFound(new ListingNotFoundException());
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("NOT_FOUND", notFound.getBody().code());
        assertEquals("The resource was not found.", notFound.getBody().message());
        assertEquals(List.of(), notFound.getBody().errors());

        ResponseEntity<ErrorResponse<ValidationError>> codeTaken = handler.handleCodeTaken(new ListingCodeTakenException());
        assertEquals(HttpStatus.CONFLICT, codeTaken.getStatusCode());
        assertEquals("CONCURRENT_MODIFICATION", codeTaken.getBody().code());
        assertEquals("Something changed at the same time. Try again.", codeTaken.getBody().message());
        assertEquals(List.of(), codeTaken.getBody().errors());

        ResponseEntity<ErrorResponse<ValidationError>> conflict =
                handler.handleConflict(new CannotAcquireLockException("lock"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("CONCURRENT_MODIFICATION", conflict.getBody().code());
        assertEquals("Something changed at the same time. Try again.", conflict.getBody().message());
    }

    @Test
    void handle_invalidOrUnreadableBody_badRequest() throws Exception {
        MapBindingResult result = new MapBindingResult(new HashMap<>(), "request");
        result.addError(new FieldError("request", "agreementId", "01jg-not-an-id", false, null, null, "pattern"));
        result.addError(new FieldError("request", "agreementId", null, false, null, null, "not null"));
        MethodParameter parameter = new MethodParameter(
                ListingExceptionHandlerTest.class.getDeclaredMethod("create", Object.class), 0);

        ResponseEntity<ErrorResponse<ValidationError>> invalid =
                handler.handleInvalidBody(new MethodArgumentNotValidException(parameter, result));

        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        assertEquals("VALIDATION_FAILED", invalid.getBody().code());
        assertEquals("Validation failed.", invalid.getBody().message());
        assertEquals(List.of(ValidationError.of("agreementId", "This value is not accepted.")),
                invalid.getBody().errors());
        assertFalse(invalid.getBody().toString().contains("01jg-not-an-id"));

        ResponseEntity<ErrorResponse<ValidationError>> unreadable = handler.handleUnreadable(
                new HttpMessageNotReadableException("Unexpected character", new EmptyInput()));

        assertEquals(HttpStatus.BAD_REQUEST, unreadable.getStatusCode());
        assertEquals("MALFORMED_REQUEST", unreadable.getBody().code());
        assertEquals("The request could not be read.", unreadable.getBody().message());
        assertEquals(List.of(), unreadable.getBody().errors());
    }

    @Test
    void handleUnexpected_securityRethrownElseInternalError() throws Exception {
        AccessDeniedException denied = new AccessDeniedException("denied");
        assertSame(denied, assertThrows(AccessDeniedException.class, () -> handler.handleUnexpected(denied)));

        ResponseEntity<ErrorResponse<ValidationError>> failure =
                handler.handleUnexpected(new IllegalStateException("an advertising reason group code 01JSECRET"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatusCode());
        assertEquals("INTERNAL_ERROR", failure.getBody().code());
        assertFalse(failure.getBody().message().contains("01JSECRET"));
        assertEquals(List.of(), failure.getBody().errors());
        assertEquals(1, logs.list.size());
        String line = logs.list.getFirst().getFormattedMessage();
        assertTrue(line.contains(IllegalStateException.class.getName()), line);
        assertTrue(line.contains(CORRELATION_ID), line);
        assertFalse(line.contains("01JSECRET"), line);
    }

    @SuppressWarnings("unused")
    private void create(Object request) {
    }

    /** A request body with nothing in it. */
    private static final class EmptyInput implements HttpInputMessage {

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    }
}
