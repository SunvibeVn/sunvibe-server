package vn.sunvibe.survey.core.presentation.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.util.DisconnectedClientHelper;
import tools.jackson.core.JacksonException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;
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
import vn.sunvibe.survey.core.domain.exception.appointment.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.appointment.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistSubmittedConflictException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.appointment.PerformerMismatchException;

import java.sql.SQLException;
import java.util.List;

import static vn.sunvibe.shared.exception.ApiErrorSupport.anyInChain;
import static vn.sunvibe.shared.exception.ApiErrorSupport.findCause;
import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.concurrentModification;

/**
 * ADR-010 error bodies of the survey endpoints (survey-schedule spec 7.3 and the refusal table of 5.4, survey-checklist
 * spec 7.3): a stable code, the failing fields ({@code []} when none) and the correlation id. Messages are English and
 * never contain a submitted value. 401 and 403 stay with Spring Security: its exceptions are rethrown. Nothing here
 * logs request data.
 * <p>
 * This is the one survey advice: every handler stays here, in this order, so Spring picks the same one for an exception
 * or a cause in its chain (clean-up plan amendment 2026-11-08, SV-272). A handler's body is in the class of its area:
 * {@link CommonExceptionHandler}, {@link AppointmentExceptionHandler}, {@link ChecklistExceptionHandler} or
 * {@link MediaExceptionHandler}. The 500 and its log lines stay here.
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
    static final String INVALID_VALUE_MESSAGE = "Value is not valid for this field.";

    // ==================== 400 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        return CommonExceptionHandler.handleInvalidBody(e);
    }

    @ExceptionHandler(ChecklistValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistValidation(ChecklistValidationException e) {
        return ChecklistExceptionHandler.handleChecklistValidation(e);
    }

    @ExceptionHandler(InvalidSurveyValueException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidValue(InvalidSurveyValueException e) {
        return CommonExceptionHandler.handleInvalidValue(e);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return CommonExceptionHandler.handleTypeMismatch(e);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return CommonExceptionHandler.handleMissingParameter(e);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMethodValidation(HandlerMethodValidationException e) {
        return CommonExceptionHandler.handleMethodValidation(e);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleConstraintViolation(ConstraintViolationException e) {
        return CommonExceptionHandler.handleConstraintViolation(e);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return CommonExceptionHandler.handleUnreadableBody(e);
    }

    // ==================== 404 ====================

    @ExceptionHandler(SurveyLeadNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(SurveyLeadNotFoundException e) {
        return CommonExceptionHandler.handleNotFound(e);
    }

    @ExceptionHandler(ChecklistNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistNotFound(ChecklistNotFoundException e) {
        return ChecklistExceptionHandler.handleChecklistNotFound(e);
    }

    // ==================== 409 ====================

    @ExceptionHandler(SurveyConcurrentModificationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleStaleVersion(SurveyConcurrentModificationException e) {
        return CommonExceptionHandler.handleStaleVersion(e);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse<ValidationError>> handleOptimisticLock(Exception e) {
        return CommonExceptionHandler.handleOptimisticLock(e);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleLockFailure(ConcurrencyFailureException e) {
        return CommonExceptionHandler.handleLockFailure(e);
    }

    @ExceptionHandler(AppointmentStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleAppointmentState(AppointmentStateException e) {
        return AppointmentExceptionHandler.handleAppointmentState(e);
    }

    @ExceptionHandler(InvalidSurveyStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleLeadState(InvalidSurveyStateException e) {
        return CommonExceptionHandler.handleLeadState(e);
    }

    @ExceptionHandler(AppointmentAlreadyScheduledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleAlreadyScheduled(
            AppointmentAlreadyScheduledException e) {
        return AppointmentExceptionHandler.handleAlreadyScheduled(e);
    }

    @ExceptionHandler(SurveyNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotAllowed(SurveyNotAllowedException e) {
        return CommonExceptionHandler.handleNotAllowed(e);
    }

    @ExceptionHandler(ChecklistNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistNotAllowed(ChecklistNotAllowedException e) {
        return ChecklistExceptionHandler.handleChecklistNotAllowed(e);
    }

    @ExceptionHandler(ChecklistAlreadyStartedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistAlreadyStarted(
            ChecklistAlreadyStartedException e) {
        return ChecklistExceptionHandler.handleChecklistAlreadyStarted(e);
    }

    @ExceptionHandler(ChecklistReadOnlyException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistReadOnly(ChecklistReadOnlyException e) {
        return ChecklistExceptionHandler.handleChecklistReadOnly(e);
    }

    @ExceptionHandler(ChecklistSubmissionNotAvailableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleSubmissionNotAvailable(
            ChecklistSubmissionNotAvailableException e) {
        return ChecklistExceptionHandler.handleSubmissionNotAvailable(e);
    }

    @ExceptionHandler(ChecklistNotReadyException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotReady(ChecklistNotReadyException e) {
        return ChecklistExceptionHandler.handleNotReady(e);
    }

    @ExceptionHandler(ChecklistReviewNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleReviewNotAllowed(ChecklistReviewNotAllowedException e) {
        return ChecklistExceptionHandler.handleReviewNotAllowed(e);
    }

    @ExceptionHandler(ChecklistDecisionNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleDecisionNotAllowed(
            ChecklistDecisionNotAllowedException e) {
        return ChecklistExceptionHandler.handleDecisionNotAllowed(e);
    }

    @ExceptionHandler(ChecklistSubmittedConflictException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleSubmittedConflict(
            ChecklistSubmittedConflictException e) {
        return ChecklistExceptionHandler.handleSubmittedConflict(e);
    }

    @ExceptionHandler(ChecklistTakeoverRequiredException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleChecklistTakeoverRequired(
            ChecklistTakeoverRequiredException e) {
        return ChecklistExceptionHandler.handleChecklistTakeoverRequired(e);
    }

    @ExceptionHandler(NoCurrentVisitException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNoCurrentVisit(NoCurrentVisitException e) {
        return AppointmentExceptionHandler.handleNoCurrentVisit(e);
    }

    // ==================== survey-media (spec 7.3) ====================

    @ExceptionHandler(MediaBodyTooLargeException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaBodyTooLarge(MediaBodyTooLargeException e) {
        return MediaExceptionHandler.handleMediaBodyTooLarge(e);
    }

    @ExceptionHandler(MediaUploadDisabledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadDisabled(MediaUploadDisabledException e) {
        return MediaExceptionHandler.handleMediaUploadDisabled(e);
    }

    @ExceptionHandler(MediaContentIdentityException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaContentIdentity(
            MediaContentIdentityException e) {
        return MediaExceptionHandler.handleMediaContentIdentity(e);
    }

    @ExceptionHandler(MediaLimitReachedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaLimitReached(MediaLimitReachedException e) {
        return MediaExceptionHandler.handleMediaLimitReached(e);
    }

    @ExceptionHandler(MediaUploadKeyReusedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadKeyReused(
            MediaUploadKeyReusedException e) {
        return MediaExceptionHandler.handleMediaUploadKeyReused(e);
    }

    @ExceptionHandler(MediaRateLimitedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaRateLimited(MediaRateLimitedException e) {
        return MediaExceptionHandler.handleMediaRateLimited(e);
    }

    @ExceptionHandler(MediaIntakePausedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaIntakePaused(MediaIntakePausedException e) {
        return MediaExceptionHandler.handleMediaIntakePaused(e);
    }

    @ExceptionHandler(MediaRequestMalformedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaRequestMalformed(
            MediaRequestMalformedException e) {
        return MediaExceptionHandler.handleMediaRequestMalformed(e);
    }

    @ExceptionHandler(MediaBodyIncompleteException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaBodyIncomplete(MediaBodyIncompleteException e) {
        return MediaExceptionHandler.handleMediaBodyIncomplete(e);
    }

    @ExceptionHandler(MediaContentTooLargeException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaContentTooLarge(MediaContentTooLargeException e) {
        return MediaExceptionHandler.handleMediaContentTooLarge(e);
    }

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaNotFound(MediaNotFoundException e,
                                                                             HttpServletRequest request) {
        return MediaExceptionHandler.handleMediaNotFound(e, request);
    }

    @ExceptionHandler(MediaNotAvailableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaNotAvailable(MediaNotAvailableException e) {
        return MediaExceptionHandler.handleMediaNotAvailable(e);
    }

    @ExceptionHandler(MediaReferencedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaReferenced(MediaReferencedException e) {
        return MediaExceptionHandler.handleMediaReferenced(e);
    }

    @ExceptionHandler(MediaUploadStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaUploadState(MediaUploadStateException e) {
        return MediaExceptionHandler.handleMediaUploadState(e);
    }

    @ExceptionHandler(MediaServerBusyException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaServerBusy(MediaServerBusyException e) {
        return MediaExceptionHandler.handleMediaServerBusy(e);
    }

    @ExceptionHandler(MediaServingDisabledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaServingDisabled(MediaServingDisabledException e) {
        return MediaExceptionHandler.handleMediaServingDisabled(e);
    }

    @ExceptionHandler(MediaMethodNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaMethodNotAllowed(
            MediaMethodNotAllowedException e) {
        return MediaExceptionHandler.handleMediaMethodNotAllowed(e);
    }

    @ExceptionHandler(MediaStoreUnavailableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMediaStoreUnavailable(
            MediaStoreUnavailableException e) {
        return MediaExceptionHandler.handleMediaStoreUnavailable(e);
    }

    // ==================== 422 ====================

    @ExceptionHandler(SurveyorIneligibleException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleIneligible(SurveyorIneligibleException e) {
        return CommonExceptionHandler.handleIneligible(e);
    }

    @ExceptionHandler(PerformerMismatchException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handlePerformerMismatch(PerformerMismatchException e) {
        return AppointmentExceptionHandler.handlePerformerMismatch(e);
    }

    // ==================== Everything else ====================

    /**
     * Spring Security exceptions are rethrown so its own 401/403 bodies are kept. An optimistic-lock failure wrapped
     * by the transaction commit is the 409 of {@link #handleOptimisticLock}. Another Spring MVC error keeps its status
     * with {@code MALFORMED_REQUEST}. A request the client abandoned (Spring's own check, see
     * {@link DisconnectedClientHelper}) is logged at DEBUG and answered without a body (SV-268). Anything else is a 500
     * whose details stay out of the response.
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
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            // The client closed the tab or the connection: nobody reads a body, and it is not a server fault
            log.debug("Client abandoned the request in survey: {} (correlationId={})", e.getClass().getName(),
                    CorrelationId.current());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (anyInChain(e, SurveyExceptionHandler::quotesRequestData)) {
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

    /**
     * Database, JSON and validation errors may quote request data in their message. {@code anyInChain} applies it to
     * the exception, its causes and what was suppressed along the way.
     */
    private static boolean quotesRequestData(Throwable e) {
        return e instanceof DataAccessException || e instanceof SQLException || e instanceof JacksonException
                || e instanceof jakarta.validation.ValidationException;
    }
}
