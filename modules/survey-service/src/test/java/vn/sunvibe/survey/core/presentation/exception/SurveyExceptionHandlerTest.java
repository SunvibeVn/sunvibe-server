package vn.sunvibe.survey.core.presentation.exception;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentTooLargeException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaServerBusyException;
import vn.sunvibe.survey.core.application.exception.media.MediaStoreUnavailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadStateException;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.media.MediaLimitReachedException;
import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadKeyReusedException;
import vn.sunvibe.survey.core.application.exception.appointment.NoCurrentVisitException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.common.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.PerformerMismatchException;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;
import vn.sunvibe.survey.core.presentation.filter.MediaBodyTooLargeException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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

    /** survey-checklist submission spec 5.4: never a 500. */
    @Test
    void lockFailures_are409ConcurrentModification() throws Exception {
        for (Exception e : List.of(new CannotAcquireLockException("deadlock detected"),
                new PessimisticLockingFailureException("lock timeout"))) {
            ResponseEntity<ErrorResponse<ValidationError>> response = handle(e);
            assertEquals(409, response.getStatusCode().value(), e.getClass().getName());
            assertEquals("CONCURRENT_MODIFICATION", response.getBody().code());
            assertEquals("expectedVersion", response.getBody().errors().get(0).field());
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

    // ==================== survey-checklist spec 7.3 ====================

    @Test
    void checklistValidation_is400WithEveryViolation() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new ChecklistValidationException(List.of(
                new Violation("content.spaces.1.code", "content.spaces.1.code is already used."),
                new Violation("content.observedAddress.value.wardCode", "Choose a ward of the province."))));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
        assertEquals(List.of("content.observedAddress.value.wardCode", "content.spaces.1.code"),
                response.getBody().errors().stream().map(ValidationError::field).toList());
    }

    @Test
    void checklistNotFound_is404WithAChecklistMessage() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new ChecklistNotFoundException());

        assertCode(response, 404, "NOT_FOUND", null);
        assertEquals("Checklist not found.", response.getBody().message());
    }

    @Test
    void checklistRefusals_haveTheirCodesAndFields() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> intent = handle(
                new ChecklistNotAllowedException("intent", List.of("LONG_TERM_RENT")));
        assertCode(intent, 409, "CHECKLIST_NOT_ALLOWED", "intent");
        assertEquals(List.of("LONG_TERM_RENT"), intent.getBody().errors().get(0).allowedValues());
        assertCode(handle(new ChecklistNotAllowedException("holder", List.of())), 409, "CHECKLIST_NOT_ALLOWED",
                "holder");
        assertCode(handle(new ChecklistAlreadyStartedException(null)), 409, "CHECKLIST_ALREADY_STARTED", null);
        assertCode(handle(new ChecklistReadOnlyException()), 409, "CHECKLIST_READ_ONLY", null);
        assertCode(handle(new ChecklistTakeoverRequiredException()), 409, "CHECKLIST_TAKEOVER_REQUIRED", null);
    }

    @Test
    void checklistConflicts_sayTheChecklistChanged() throws Exception {
        for (String field : List.of("expectedVersion", "otherGroupVersions.parts")) {
            ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                    new SurveyConcurrentModificationException(field));
            assertCode(response, 409, "CONCURRENT_MODIFICATION", field);
            assertEquals("This checklist changed since you opened it.", response.getBody().message());
        }
        assertEquals(SurveyExceptionHandler.CONCURRENT_MODIFICATION_MESSAGE,
                handle(new SurveyConcurrentModificationException("expectedLeadVersion")).getBody().message());
    }

    @Test
    void unknownKey_is400OnItsPath() throws Exception {
        JsonMapper strict = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        JacksonException unknown = assertThrows(JacksonException.class,
                () -> strict.readValue("{\"content\":{\"name\":\"x\",\"secret\":\"0905123456\"}}", Outer.class));

        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new HttpMessageNotReadableException("JSON parse error", unknown, INPUT));

        assertCode(response, 400, "VALIDATION_FAILED", "content.secret");
        assertEquals("content.secret is not a known field.", response.getBody().errors().get(0).message());
    }

    @Test
    void beanValidationIndexes_becomeDottedPaths() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Outer(null), "request");
        result.addError(new FieldError("request", "spaces[1].code", "Code is required."));
        MethodParameter parameter = new MethodParameter(Outer.class.getDeclaredMethod("content"), -1);

        ResponseEntity<ErrorResponse<ValidationError>> response = handle(
                new MethodArgumentNotValidException(parameter, result));

        assertCode(response, 400, "VALIDATION_FAILED", "spaces.1.code");
    }

    private static final HttpInputMessage INPUT = new HttpInputMessage() {
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    };

    record Outer(Inner content) {
    }

    record Inner(String name) {
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

    // ==================== survey-media spec 7.3 ====================

    @Test
    void mediaUploadDisabled_is409WithoutField() throws Exception {
        assertCode(handle(new MediaUploadDisabledException()), 409, "MEDIA_UPLOAD_DISABLED", null);
    }

    @Test
    void mediaIntakePaused_is503WithoutFieldOrRetryAfter() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new MediaIntakePausedException(true));

        assertCode(response, 503, "MEDIA_INTAKE_PAUSED", null);
        assertNull(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), "spec 7.3: no Retry-After here");
    }

    @Test
    void mediaDuplicateContent_is409OnTheHashWithTheMatchingId() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new MediaContentIdentityException(
                IdentityVerdict.Kind.DUPLICATE_CONTENT, "01JMED1A000000000000000007"));

        assertCode(response, 409, "MEDIA_DUPLICATE_CONTENT", "contentSha256");
        assertEquals(List.of("01JMED1A000000000000000007"), response.getBody().errors().get(0).allowedValues());
    }

    @Test
    void mediaFromOtherVisit_is409OnTheHashWithTheMatchingId() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> response = handle(new MediaContentIdentityException(
                IdentityVerdict.Kind.FROM_OTHER_VISIT, "01JMED1A000000000000000008"));

        assertCode(response, 409, "MEDIA_FROM_OTHER_VISIT", "contentSha256");
        assertEquals(List.of("01JMED1A000000000000000008"), response.getBody().errors().get(0).allowedValues());
    }

    @Test
    void mediaLimitReached_is409OnSelectedOrPending() throws Exception {
        assertCode(handle(new MediaLimitReachedException("selected")), 409, "MEDIA_LIMIT_REACHED", "selected");
        assertCode(handle(new MediaLimitReachedException("pending")), 409, "MEDIA_LIMIT_REACHED", "pending");
    }

    @Test
    void mediaUploadKeyReused_is409OnTheKey() throws Exception {
        assertCode(handle(new MediaUploadKeyReusedException()), 409, "IDEMPOTENCY_KEY_REUSED", "uploadKey");
    }

    @Test
    void mediaRateLimited_is429OnTheFieldWithRetryAfter() throws Exception {
        for (String field : List.of("records", "bytes", "expensive")) {
            ResponseEntity<ErrorResponse<ValidationError>> response = handle(new MediaRateLimitedException(field,
                    600));

            assertCode(response, 429, "RATE_LIMITED", field);
            assertEquals("600", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), field);
        }
    }

    @Test
    void mediaBodyTooLarge_is413_alsoWhenJacksonWrapsIt() throws Exception {
        assertCode(handle(new MediaBodyTooLargeException()), 413, "PAYLOAD_TOO_LARGE", null);
        assertCode(handle(new HttpMessageNotReadableException("I/O error", new MediaBodyTooLargeException(), INPUT)),
                413, "PAYLOAD_TOO_LARGE", null);
    }

    @Test
    void mediaContentHeaders_415OnContentType_400OnLengthDigestOrBody_413() throws Exception {
        assertCode(handle(new MediaRequestMalformedException("Content-Type", true)), 415, "MALFORMED_REQUEST",
                "Content-Type");
        for (String field : List.of("Content-Length", "Content-Digest", "body")) {
            assertCode(handle(new MediaRequestMalformedException(field, false)), 400, "MALFORMED_REQUEST", field);
        }
        assertCode(handle(new MediaBodyIncompleteException()), 400, "MALFORMED_REQUEST", "body");
        assertCode(handle(new MediaContentTooLargeException()), 413, "PAYLOAD_TOO_LARGE", null);
    }

    @Test
    void mediaUploadStates_409WithTheirCodes() throws Exception {
        assertCode(handle(new MediaNotAvailableException("uploadedBy")), 409, "MEDIA_NOT_AVAILABLE", "uploadedBy");
        assertCode(handle(new MediaNotAvailableException("status")), 409, "MEDIA_NOT_AVAILABLE", "status");
        assertCode(handle(new MediaUploadStateException(MediaUploadStateException.Kind.UPLOAD_EXPIRED)), 409,
                "MEDIA_UPLOAD_EXPIRED", null);
        assertCode(handle(new MediaUploadStateException(MediaUploadStateException.Kind.UPLOAD_IN_PROGRESS)), 409,
                "MEDIA_UPLOAD_IN_PROGRESS", null);
        assertCode(handle(new MediaUploadStateException(MediaUploadStateException.Kind.CONTENT_ALREADY_RECEIVED)),
                409, "MEDIA_CONTENT_ALREADY_RECEIVED", null);
    }

    @Test
    void mediaServerBusy_503WithRetryAfter_storeUnavailable_503Without() throws Exception {
        ResponseEntity<ErrorResponse<ValidationError>> busy = handle(new MediaServerBusyException(10));
        assertCode(busy, 503, "MEDIA_SERVER_BUSY", null);
        assertEquals("10", busy.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        ResponseEntity<ErrorResponse<ValidationError>> store = handle(new MediaStoreUnavailableException());
        assertCode(store, 503, "MEDIA_STORE_UNAVAILABLE", null);
        assertNull(store.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void mediaNotFound_404_closesTheConnectionOnAContentPath() {
        org.springframework.mock.web.MockHttpServletRequest content =
                new org.springframework.mock.web.MockHttpServletRequest("PUT",
                        "/api/v1/staff/survey-media/01JMED1A000000000000000001/content");
        org.springframework.mock.web.MockHttpServletRequest removal =
                new org.springframework.mock.web.MockHttpServletRequest("POST",
                        "/api/v1/staff/survey-media/01JMED1A000000000000000001/removal");

        ResponseEntity<ErrorResponse<ValidationError>> onContent = handler.handleMediaNotFound(
                new MediaNotFoundException(), content);
        ResponseEntity<ErrorResponse<ValidationError>> onRemoval = handler.handleMediaNotFound(
                new MediaNotFoundException(), removal);

        assertCode(onContent, 404, "NOT_FOUND", null);
        assertEquals("close", onContent.getHeaders().getFirst(HttpHeaders.CONNECTION));
        assertNull(onRemoval.getHeaders().getFirst(HttpHeaders.CONNECTION));
        assertEquals(onContent.getBody().message(), onRemoval.getBody().message());
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
