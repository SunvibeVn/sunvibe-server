package vn.sunvibe.brokerage.core.presentation.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A query value of the wrong type on the agreement list or history (E05-S7 spec S7-D13 (3), EH-1; ADR-010): 400
 * VALIDATION_FAILED with one entry naming the parameter, never its value, and no log line. Built as
 * {@link BrokerageExceptionHandlerTest} builds the handler and its log appender.
 */
class BrokerageExceptionHandlerTypeMismatchTest {

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
    void handleTypeMismatch_pageNotAnInteger_validationFailedNamingPage() throws Exception {
        MethodParameter parameter = new MethodParameter(
                BrokerageExceptionHandlerTypeMismatchTest.class.getDeclaredMethod("list", int.class), 0);

        ResponseEntity<ErrorResponse<ValidationError>> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("x", Integer.class, "page", parameter,
                        new NumberFormatException("For input string: \"x\"")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorCode.VALIDATION_FAILED, response.getBody().code());
        assertEquals("Validation failed.", response.getBody().message());
        assertEquals(1, response.getBody().errors().size());
        ValidationError entry = response.getBody().errors().getFirst();
        assertEquals("page", entry.field());
        assertEquals("This value is not accepted.", entry.message());
        assertNull(entry.allowedValues());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
        for (String text : List.of(response.getBody().code(), response.getBody().message(), entry.field(),
                entry.message())) {
            assertFalse(text.contains("x"), text);
        }
        assertTrue(logs.list.isEmpty(), "a refused parameter is not logged");
    }

    /** The shape of a list parameter, for the method parameter of the exception. */
    @SuppressWarnings("unused")
    private void list(int page) {
    }
}
