package vn.sunvibe.survey.core.presentation.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.web.MediaContentPaths;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistDecisionNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotReadyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReviewNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistSubmissionNotAvailableException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.media.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentTooLargeException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaReferencedException;
import vn.sunvibe.survey.core.application.exception.media.MediaMethodNotAllowedException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaServingDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaServerBusyException;
import vn.sunvibe.survey.core.application.exception.media.MediaStoreUnavailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.media.MediaLimitReachedException;
import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadKeyReusedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadStateException;
import vn.sunvibe.survey.core.application.exception.appointment.NoCurrentVisitException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.common.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.ChecklistSubmittedConflictException;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.PerformerMismatchException;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;
import vn.sunvibe.survey.core.presentation.filter.MediaBodyTooLargeException;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ADR-010 error bodies of the survey endpoints (survey-schedule spec 7.3 and the refusal table of 5.4, survey-checklist
 * spec 7.3): a stable code, the failing fields ({@code []} when none) and the correlation id. Messages are English and
 * never contain a submitted value. 401 and 403 stay with Spring Security: its exceptions are rethrown. Nothing here
 * logs request data.
 */
@RestControllerAdvice(basePackages = "vn.sunvibe.survey.core.presentation")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class SurveyExceptionHandler {

    static final String VALIDATION_FAILED_MESSAGE = "Validation failed.";
    static final String MALFORMED_REQUEST_MESSAGE = "Request body could not be read.";
    static final String REQUEST_NOT_PROCESSED_MESSAGE = "The request cannot be processed.";
    static final String NOT_FOUND_MESSAGE = "Lead or survey appointment not found.";
    static final String CONCURRENT_MODIFICATION_MESSAGE = "Someone else just updated this lead. Reload and try again.";
    static final String CHECKLIST_CHANGED_MESSAGE = "This checklist changed since you opened it.";
    static final String STATE_MESSAGE = "This action is not allowed in the current status.";
    static final String SUBMISSION_NOT_AVAILABLE_MESSAGE = "Submission is not available.";
    static final String NOT_READY_MESSAGE = "The checklist cannot be submitted yet.";
    static final String REVIEW_NOT_ALLOWED_MESSAGE = "You may not decide this checklist.";
    static final String DECISION_NOT_ALLOWED_MESSAGE = "This decision is not allowed now.";
    static final String INTERNAL_ERROR_MESSAGE = "Unexpected server error.";
    private static final String INVALID_VALUE_MESSAGE = "Value is not valid for this field.";
    /** How Jackson starts the message of a missing {@code @JsonProperty(required = true)} creator property. */
    private static final String MISSING_REQUIRED_PROPERTY = "Missing required creator property";
    /** The version fields of the checklist commands (survey-checklist spec 7.3). */
    private static final String CHECKLIST_VERSION = "expectedVersion";
    private static final String OTHER_GROUP_VERSIONS = "otherGroupVersions.";
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int MAX_EXCEPTIONS_CHECKED = 50;

    // ==================== 400 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        return validationFailed(fieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /** survey-checklist spec 5.5: every rule a checklist body breaks, each on its JSON path. */
    @ExceptionHandler(ChecklistValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistValidation(ChecklistValidationException e) {
        return validationFailed(e.violations().stream()
                .map(violation -> ValidationError.of(violation.path(), violation.message()))
                .toList());
    }

    /** A value refused by a command or the domain; no field means the request as a whole (e.g. nothing changed). */
    @ExceptionHandler(InvalidSurveyValueException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidValue(InvalidSurveyValueException e) {
        if (e.getField() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, e.getMessage(), List.of()));
        }
        return validationFailed(List.of(ValidationError.of(e.getField(), e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationFailed(List.of(ValidationError.of(e.getName(), "Value has the wrong type.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return validationFailed(List.of(ValidationError.of(e.getParameterName(), "Parameter is required.")));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMethodValidation(HandlerMethodValidationException e) {
        List<ValidationError> errors = new ArrayList<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors beanErrors) {
                errors.addAll(fieldErrors(beanErrors.getFieldErrors()));
            } else {
                String message = result.getResolvableErrors().isEmpty() ? null
                        : result.getResolvableErrors().get(0).getDefaultMessage();
                errors.add(ValidationError.of(parameterName(result.getMethodParameter()),
                        message == null ? INVALID_VALUE_MESSAGE : message));
            }
        }
        return validationFailed(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleConstraintViolation(ConstraintViolationException e) {
        List<ValidationError> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            errors.add(ValidationError.of(path.substring(path.lastIndexOf('.') + 1), violation.getMessage()));
        }
        return validationFailed(errors);
    }

    /**
     * A value Jackson could not convert is a validation error on its JSON path; a key that must be present but is
     * missing (survey-schedule spec A-4) is "required" on its path; a key a strict checklist body does not know
     * (survey-checklist spec A-4, plan PD-2) is "not a known field" on its path; anything else is unreadable JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadableBody(HttpMessageNotReadableException e) {
        if (findCause(e, MediaBodyTooLargeException.class) != null) {
            return payloadTooLarge();
        }
        MismatchedInputException mismatch = findCause(e, MismatchedInputException.class);
        if (mismatch != null && !mismatch.getPath().isEmpty()) {
            String path = jsonPath(mismatch);
            if (mismatch instanceof UnrecognizedPropertyException) {
                return validationFailed(List.of(ValidationError.of(path, path + " is not a known field.")));
            }
            boolean missingKey = mismatch.getOriginalMessage() != null
                    && mismatch.getOriginalMessage().startsWith(MISSING_REQUIRED_PROPERTY);
            return validationFailed(List.of(ValidationError.of(path,
                    missingKey ? path + " is required." : INVALID_VALUE_MESSAGE)));
        }
        InputCoercionException coercion = findCause(e, InputCoercionException.class);
        if (coercion != null && !coercion.getPath().isEmpty()) {
            return validationFailed(List.of(ValidationError.of(jsonPath(coercion), INVALID_VALUE_MESSAGE)));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_MESSAGE, List.of()));
    }

    // ==================== 404 ====================

    /** survey-schedule BR-1, E-1: an invisible lead or appointment answers like a missing one. */
    @ExceptionHandler(SurveyLeadNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(SurveyLeadNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE, List.of()));
    }

    /** survey-checklist spec 7.3: a missing checklist or checkpoint answers like one of a hidden lead. */
    @ExceptionHandler(ChecklistNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistNotFound(ChecklistNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, e.getMessage(), List.of()));
    }

    // ==================== 409 ====================

    @ExceptionHandler(SurveyConcurrentModificationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleStaleVersion(SurveyConcurrentModificationException e) {
        return concurrentModification(e.getField());
    }

    /** A row-version conflict of the appointment that reached the handler unmapped. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse<ValidationError>> handleOptimisticLock(Exception e) {
        return concurrentModification("expectedAppointmentVersion");
    }

    /**
     * survey-checklist submission spec 5.4: a lock wait, a deadlock victim or a serialization failure that still occurs is
     * a concurrent change, never a 500.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleLockFailure(ConcurrencyFailureException e) {
        return concurrentModification("expectedVersion");
    }

    /** survey-schedule spec 4.4: a visited or cancelled appointment is final. */
    @ExceptionHandler(AppointmentStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleAppointmentState(AppointmentStateException e) {
        return invalidState(List.of());
    }

    /** survey-schedule spec 7.3: a refused lead move ({@code []}) or a resumption of a lead not on hold. */
    @ExceptionHandler(InvalidSurveyStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleLeadState(InvalidSurveyStateException e) {
        return invalidState(e.getAllowedValues());
    }

    @ExceptionHandler(AppointmentAlreadyScheduledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleAlreadyScheduled(
            AppointmentAlreadyScheduledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SURVEY_ALREADY_SCHEDULED, e.getMessage(), List.of()));
    }

    @ExceptionHandler(SurveyNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotAllowed(SurveyNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SURVEY_NOT_ALLOWED, e.getMessage(),
                        List.of(ValidationError.withAllowed(e.getField(), e.getMessage(), e.getAllowedValues()))));
    }

    /** survey-checklist spec 7.3: the visit, the intent, the lead status or the holder does not allow the command. */
    @ExceptionHandler(ChecklistNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistNotAllowed(ChecklistNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.CHECKLIST_NOT_ALLOWED, e.getMessage(),
                        List.of(ValidationError.withAllowed(e.getField(), e.getMessage(), e.getAllowedValues()))));
    }

    @ExceptionHandler(ChecklistAlreadyStartedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistAlreadyStarted(
            ChecklistAlreadyStartedException e) {
        return conflict(ErrorCode.CHECKLIST_ALREADY_STARTED, e.getMessage());
    }

    /** survey-checklist spec 4.4: the reason is on the next read ({@code readOnlyReason}). */
    @ExceptionHandler(ChecklistReadOnlyException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistReadOnly(ChecklistReadOnlyException e) {
        return conflict(ErrorCode.CHECKLIST_READ_ONLY, e.getMessage());
    }

    /** Gate S2 (submission spec 7.3): uploads or submission are off. */
    @ExceptionHandler(ChecklistSubmissionNotAvailableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleSubmissionNotAvailable(
            ChecklistSubmissionNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                ErrorCode.CHECKLIST_SUBMISSION_NOT_AVAILABLE, SUBMISSION_NOT_AVAILABLE_MESSAGE,
                List.of(ValidationError.of("submission", SUBMISSION_NOT_AVAILABLE_MESSAGE))));
    }

    /** Gates S3..S7 (submission spec 7.3): one entry per failing gate; the read shows the details. */
    @ExceptionHandler(ChecklistNotReadyException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotReady(ChecklistNotReadyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.CHECKLIST_NOT_READY,
                NOT_READY_MESSAGE, e.fields().stream().map(field -> ValidationError.of(field, NOT_READY_MESSAGE))
                        .toList()));
    }

    /** submission spec 7.3: the caller is in the excluded set, or cannot sign in. */
    @ExceptionHandler(ChecklistReviewNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleReviewNotAllowed(ChecklistReviewNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                ErrorCode.CHECKLIST_REVIEW_NOT_ALLOWED, REVIEW_NOT_ALLOWED_MESSAGE,
                List.of(ValidationError.of("reviewer", REVIEW_NOT_ALLOWED_MESSAGE))));
    }

    /** submission spec 7.3: one entry per failed rule; the read shows which PASSED rules fail now. */
    @ExceptionHandler(ChecklistDecisionNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleDecisionNotAllowed(
            ChecklistDecisionNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                ErrorCode.CHECKLIST_DECISION_NOT_ALLOWED, DECISION_NOT_ALLOWED_MESSAGE, e.fields().stream()
                .map(field -> ValidationError.of(field, DECISION_NOT_ALLOWED_MESSAGE)).toList()));
    }

    /** plan PD-8: a SUBMITTED revision of the cycle, or its unmarked PASSED, was committed first. */
    @ExceptionHandler(ChecklistSubmittedConflictException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleSubmittedConflict(
            ChecklistSubmittedConflictException e) {
        return handleStaleVersion(new SurveyConcurrentModificationException("expectedVersion"));
    }

    @ExceptionHandler(ChecklistTakeoverRequiredException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistTakeoverRequired(
            ChecklistTakeoverRequiredException e) {
        return conflict(ErrorCode.CHECKLIST_TAKEOVER_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(NoCurrentVisitException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNoCurrentVisit(NoCurrentVisitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.NO_CURRENT_VISIT, e.getMessage(), List.of()));
    }

    // ==================== survey-media (spec 7.3) ====================

    /** survey-media spec A-11: a JSON body above 16 KiB, stopped while it was read. */
    @ExceptionHandler(MediaBodyTooLargeException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaBodyTooLarge(MediaBodyTooLargeException e) {
        return payloadTooLarge();
    }

    /** survey-media spec A-1: uploads are off. */
    @ExceptionHandler(MediaUploadDisabledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadDisabled(MediaUploadDisabledException e) {
        return conflict(ErrorCode.MEDIA_UPLOAD_DISABLED, e.getMessage());
    }

    /** survey-media spec A-16: the matching photo's id in {@code allowedValues} of {@code contentSha256}. */
    @ExceptionHandler(MediaContentIdentityException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaContentIdentity(
            MediaContentIdentityException e) {
        String code = e.getKind() == IdentityVerdict.Kind.FROM_OTHER_VISIT ? ErrorCode.MEDIA_FROM_OTHER_VISIT
                : ErrorCode.MEDIA_DUPLICATE_CONTENT;
        List<String> matching = e.getMatchingMediaIdOrNull() == null ? List.of()
                : List.of(e.getMatchingMediaIdOrNull());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(code, e.getMessage(),
                List.of(ValidationError.withAllowed("contentSha256", e.getMessage(), matching))));
    }

    /** survey-media BR-6, A-3: field {@code selected} or {@code pending}. */
    @ExceptionHandler(MediaLimitReachedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaLimitReached(MediaLimitReachedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.MEDIA_LIMIT_REACHED,
                e.getMessage(), List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 4.4 step 1: the upload key names another body. */
    @ExceptionHandler(MediaUploadKeyReusedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadKeyReused(
            MediaUploadKeyReusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                e.getMessage(), List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 7.3: 429 on the field, {@code Retry-After} always. */
    @ExceptionHandler(MediaRateLimitedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaRateLimited(MediaRateLimitedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ErrorCode.RATE_LIMITED, e.getMessage(),
                        List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec A-18: retry the same request later; no {@code Retry-After} (spec 7.3). */
    @ExceptionHandler(MediaIntakePausedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaIntakePaused(MediaIntakePausedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(ErrorCode.MEDIA_INTAKE_PAUSED, e.getMessage(), List.of()));
    }

    /** survey-media spec 5.4 content (1), (12), 4.4 step 2: 415 on Content-Type, else 400 on the field. */
    @ExceptionHandler(MediaRequestMalformedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaRequestMalformed(
            MediaRequestMalformedException e) {
        return ResponseEntity.status(e.isUnsupportedMediaType() ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                : HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, e.getMessage(),
                List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 4.4 step 2: the body ended early, failed or passed its deadline; nothing is stored. */
    @ExceptionHandler(MediaBodyIncompleteException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaBodyIncomplete(MediaBodyIncompleteException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST,
                e.getMessage(), List.of(ValidationError.of("body", e.getMessage()))));
    }

    /** survey-media spec A-2: a declared length above 20 MiB. */
    @ExceptionHandler(MediaContentTooLargeException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaContentTooLarge(MediaContentTooLargeException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, e.getMessage(), List.of()));
    }

    /**
     * survey-media spec 4.3: one 404 body for a missing photo and one of a hidden lead; on a content path it closes the
     * connection (A-13), since the body was not read.
     */
    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaNotFound(MediaNotFoundException e,
                                                                             HttpServletRequest request) {
        ResponseEntity.BodyBuilder answer = ResponseEntity.status(HttpStatus.NOT_FOUND);
        if (MediaContentPaths.isContentPath(request.getRequestURI())) {
            answer.header(HttpHeaders.CONNECTION, "close");
        }
        return answer.body(ErrorResponse.of(ErrorCode.NOT_FOUND, e.getMessage(), List.of()));
    }

    /** survey-media spec 5.4 content (8): field {@code uploadedBy} or {@code status}. */
    @ExceptionHandler(MediaNotAvailableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaNotAvailable(MediaNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.MEDIA_NOT_AVAILABLE,
                e.getMessage(), List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    /** survey-media spec 4.7, 5.4 removal (8). */
    @ExceptionHandler(MediaReferencedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaReferenced(MediaReferencedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.MEDIA_REFERENCED,
                e.getMessage(), List.of()));
    }

    /** survey-media spec 5.4 content (9). */
    @ExceptionHandler(MediaUploadStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadState(MediaUploadStateException e) {
        String code = switch (e.getKind()) {
            case CONTENT_ALREADY_RECEIVED -> ErrorCode.MEDIA_CONTENT_ALREADY_RECEIVED;
            case UPLOAD_IN_PROGRESS -> ErrorCode.MEDIA_UPLOAD_IN_PROGRESS;
            case UPLOAD_EXPIRED -> ErrorCode.MEDIA_UPLOAD_EXPIRED;
        };
        return conflict(code, e.getMessage());
    }

    /** survey-media spec A-3, A-25: 503 with {@code Retry-After}. */
    @ExceptionHandler(MediaServerBusyException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaServerBusy(MediaServerBusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(ErrorResponse.of(ErrorCode.MEDIA_SERVER_BUSY, e.getMessage(), List.of()));
    }

    /** survey-media spec A-24: serving is off; {@code Retry-After}. */
    @ExceptionHandler(MediaServingDisabledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaServingDisabled(MediaServingDisabledException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(MediaServingDisabledException.RETRY_AFTER_SECONDS))
                .body(ErrorResponse.of(ErrorCode.MEDIA_SERVING_DISABLED, e.getMessage(), List.of()));
    }

    /** survey-media spec 7.1, 7.3: the content read is GET only; HEAD 405 with {@code Allow: GET}. */
    @ExceptionHandler(MediaMethodNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaMethodNotAllowed(
            MediaMethodNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "GET")
                .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage(), List.of()));
    }

    /** survey-media spec 4.4 step 2, A-18: the same request may be sent again; no {@code Retry-After} (7.3). */
    @ExceptionHandler(MediaStoreUnavailableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaStoreUnavailable(
            MediaStoreUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(ErrorCode.MEDIA_STORE_UNAVAILABLE, e.getMessage(), List.of()));
    }

    // ==================== 422 ====================

    @ExceptionHandler(SurveyorIneligibleException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleIneligible(SurveyorIneligibleException e) {
        List<ValidationError> errors = e.getField() == null ? List.of()
                : List.of(ValidationError.of(e.getField(), e.getMessage()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ErrorResponse.of(ErrorCode.SURVEYOR_INELIGIBLE, e.getMessage(), errors));
    }

    @ExceptionHandler(PerformerMismatchException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handlePerformerMismatch(PerformerMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ErrorResponse.of(ErrorCode.PERFORMER_MISMATCH, e.getMessage(),
                        List.of(ValidationError.of("performedByUserId", e.getMessage()))));
    }

    // ==================== Everything else ====================

    /**
     * Spring Security exceptions are rethrown so its own 401/403 bodies are kept. An optimistic-lock failure wrapped
     * by the transaction commit is the 409 of {@link #handleOptimisticLock}. Another Spring MVC error keeps its status
     * with {@code MALFORMED_REQUEST}. Anything else is a 500 whose details stay out of the response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (findCause(e, OptimisticLockException.class) != null
                || findCause(e, ObjectOptimisticLockingFailureException.class) != null) {
            return concurrentModification("expectedAppointmentVersion");
        }
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .headers(springError.getHeaders())
                    .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, REQUEST_NOT_PROCESSED_MESSAGE, List.of()));
        }
        if (carriesRequestData(e)) {
            // The message and the stack trace can quote submitted values, e.g. a SQL key
            log.error("Unexpected error in survey: {} (correlationId={})", e.getClass().getName(),
                    CorrelationId.current());
        } else {
            log.error("Unexpected error in survey: {} (correlationId={})", e.getClass().getName(),
                    CorrelationId.current(), e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE, List.of()));
    }

    // ==================== Helpers ====================

    /** A stale version; on a checklist version (survey-checklist spec 7.3) the message says the checklist changed. */
    private static ResponseEntity<ErrorResponse<ValidationError>> concurrentModification(String field) {
        String message = CHECKLIST_VERSION.equals(field) || field.startsWith(OTHER_GROUP_VERSIONS)
                ? CHECKLIST_CHANGED_MESSAGE : CONCURRENT_MODIFICATION_MESSAGE;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION, message,
                        List.of(ValidationError.of(field, message))));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> payloadTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE,
                MediaBodyTooLargeException.MESSAGE, List.of()));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> conflict(String code, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(code, message, List.of()));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> invalidState(List<String> allowed) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.INVALID_STATE_TRANSITION, STATE_MESSAGE,
                        List.of(ValidationError.withAllowed("status", STATE_MESSAGE, allowed))));
    }

    /** One entry per field (the first message), sorted by field so the body does not depend on check order. */
    private static ResponseEntity<ErrorResponse<ValidationError>> validationFailed(List<ValidationError> errors) {
        Map<String, ValidationError> byField = new LinkedHashMap<>();
        errors.forEach(error -> byField.putIfAbsent(error.field(), error));
        List<ValidationError> sorted = byField.values().stream()
                .sorted(Comparator.comparing(ValidationError::field))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, VALIDATION_FAILED_MESSAGE, sorted));
    }

    /** Bean Validation paths with dotted indexes, like JSON paths: {@code spaces[1].code} is {@code spaces.1.code}. */
    private static List<ValidationError> fieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> ValidationError.of(INDEX.matcher(error.getField()).replaceAll(".$1"),
                        error.getDefaultMessage() == null ? INVALID_VALUE_MESSAGE : error.getDefaultMessage()))
                .toList();
    }

    /** The name the client used: the query or path name when the parameter has one. */
    private static String parameterName(MethodParameter parameter) {
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        return parameter.getParameterName() == null ? "request" : parameter.getParameterName();
    }

    /** The JSON path of the value Jackson refused, property names (or array indexes) joined by dots. */
    private static String jsonPath(JacksonException e) {
        List<String> parts = new ArrayList<>();
        for (JacksonException.Reference reference : e.getPath()) {
            parts.add(reference.getPropertyName() != null
                    ? reference.getPropertyName() : String.valueOf(reference.getIndex()));
        }
        return String.join(".", parts);
    }

    /**
     * True when the exception, one of its causes or one of the exceptions suppressed along the way may quote request
     * data in its message: database, JSON and validation errors.
     */
    private static boolean carriesRequestData(Throwable e) {
        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(e);
        while (!pending.isEmpty() && seen.size() < MAX_EXCEPTIONS_CHECKED) {
            Throwable current = pending.poll();
            if (!seen.add(current)) {
                continue;
            }
            if (current instanceof DataAccessException || current instanceof SQLException
                    || current instanceof JacksonException
                    || current instanceof jakarta.validation.ValidationException) {
                return true;
            }
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            pending.addAll(List.of(current.getSuppressed()));
        }
        return !pending.isEmpty();
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
