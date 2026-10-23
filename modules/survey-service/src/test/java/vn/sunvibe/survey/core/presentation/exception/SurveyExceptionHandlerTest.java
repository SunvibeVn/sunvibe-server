package vn.sunvibe.survey.core.presentation.exception;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.survey.core.application.exception.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.NoCurrentVisitException;
import vn.sunvibe.survey.core.application.exception.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.PerformerMismatchException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Status, code and field of every survey refusal (survey-schedule spec 7.3, refusal table of 5.4; ADR-010). Each
 * exception goes through the handler method Spring MVC would pick. 401 and 403 stay with Spring Security.
 */
class SurveyExceptionHandlerTest {

    private static final String CORRELATION_ID = "01J0000000000000000000TEST";

    private final SurveyExceptionHandler handler = new SurveyExceptionHandler();
    private final ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(
            SurveyExceptionHandler.class);

    @BeforeEach
    void setUp() {
        MDC.put(CorrelationId.MDC_KEY, CORRELATION_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void invalidValue_is400OnTheField() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new InvalidSurveyValueException("startAt", "startAt must be a whole minute."));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
        assertEquals("startAt", response.getBody().errors().get(0).field());
        assertEquals(CORRELATION_ID, response.getBody().correlationId());
    }

    @Test
    void nothingChanged_is400WithoutErrors() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new InvalidSurveyValueException(null, "Nothing changed."));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
        assertEquals("Nothing changed.", response.getBody().message());
        assertEquals(List.of(), response.getBody().errors());
    }

    @Test
    void notFound_is404WithoutField() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new SurveyLeadNotFoundException());

        assertEquals(404, response.getStatusCode().value());
        assertEquals("NOT_FOUND", response.getBody().code());
        assertEquals(List.of(), response.getBody().errors());
    }

    @Test
    void staleVersions_are409OnTheirField() throws Exception {
        for (String field : List.of("expectedLeadVersion", "expectedAppointmentVersion")) {
            ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                    new SurveyConcurrentModificationException(field));
            assertEquals(409, response.getStatusCode().value());
            assertEquals("CONCURRENT_MODIFICATION", response.getBody().code());
            assertEquals(field, response.getBody().errors().get(0).field());
        }
    }

    @Test
    void optimisticLockLeftInTheCauseChain_is409OnTheAppointmentVersion() throws Exception {
        for (Exception e : List.of(new ObjectOptimisticLockingFailureException("SurveyAppointmentEntity", "x"),
                new TransactionSystemException("commit failed", new OptimisticLockException("stale")))) {
            ResponseEntity<ErrorResponse<ValidationError>> response = handle(e);
            assertEquals(409, response.getStatusCode().value(), e.getClass().getName());
            assertEquals("CONCURRENT_MODIFICATION", response.getBody().code());
            assertEquals("expectedAppointmentVersion", response.getBody().errors().get(0).field());
        }
    }

    @Test
    void stateRefusals_are409InvalidStateTransitionOnStatus() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> appointment = handle(new AppointmentStateException("VISITED"));
        ResponseEntity<ErrorResponse<ValidationError>> resume = handle(
                new InvalidSurveyStateException(List.of("ON_HOLD")));

        assertEquals(409, appointment.getStatusCode().value());
        assertEquals("INVALID_STATE_TRANSITION", appointment.getBody().code());
        assertEquals("status", appointment.getBody().errors().get(0).field());
        assertEquals(List.of(), appointment.getBody().errors().get(0).allowedValues());
        assertEquals(List.of("ON_HOLD"), resume.getBody().errors().get(0).allowedValues());
    }

    @Test
    void surveyRefusals_haveTheirCodes() throws Exception {
        assertCode(handle(new AppointmentAlreadyScheduledException()), 409, "SURVEY_ALREADY_SCHEDULED", null);
        assertCode(handle(new NoCurrentVisitException()), 409, "NO_CURRENT_VISIT", null);
        assertCode(handle(new SurveyorIneligibleException(null)), 422, "SURVEYOR_INELIGIBLE", null);
        assertCode(handle(new SurveyorIneligibleException("performedByUserId")), 422, "SURVEYOR_INELIGIBLE",
                "performedByUserId");
        assertCode(handle(new PerformerMismatchException()), 422, "PERFORMER_MISMATCH", "performedByUserId");
        ResponseEntity<ErrorResponse<ValidationError>> notAllowed = handle(
                new SurveyNotAllowedException("intent", List.of("SHORT_TERM_RENT", "LONG_TERM_RENT")));
        assertCode(notAllowed, 409, "SURVEY_NOT_ALLOWED", "intent");
        assertEquals(List.of("SHORT_TERM_RENT", "LONG_TERM_RENT"),
                notAllowed.getBody().errors().get(0).allowedValues());
    }

    @Test
    void securityExceptions_areRethrown() {
        assertThrows(AccessDeniedException.class, () -> handle(new AccessDeniedException("no")));
    }

    @Test
    void anythingElse_is500WithoutDetails() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new IllegalStateException("secret 0905123456"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertFalse(response.getBody().message().contains("secret"));
    }

    private static void assertCode(ResponseEntity<ErrorResponse<ValidationError>> response, int status, String code,
                                   String fieldOrNull) {
        assertEquals(status, response.getStatusCode().value(), code);
        assertEquals(code, response.getBody().code());
        if (fieldOrNull == null) {
            assertEquals(List.of(), response.getBody().errors(), code);
        } else {
            assertEquals(fieldOrNull, response.getBody().errors().get(0).field(), code);
        }
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ErrorResponse<ValidationError>> handle(Exception exception) throws Exception {
        Method method = resolver.resolveMethod(exception);
        assertNotNull(method, "no handler method for " + exception.getClass().getName());
        try {
            return (ResponseEntity<ErrorResponse<ValidationError>>) method.invoke(handler, exception);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
