package vn.sunvibe.survey.core.presentation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistDecisionNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotReadyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReviewNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistSubmissionNotAvailableException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistSubmittedConflictException;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;

import java.util.List;

import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.conflict;
import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.handleStaleVersion;
import static vn.sunvibe.survey.core.presentation.exception.CommonExceptionHandler.validationFailed;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.DECISION_NOT_ALLOWED_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.NOT_READY_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.REVIEW_NOT_ALLOWED_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.SUBMISSION_NOT_AVAILABLE_MESSAGE;

/**
 * The bodies of the checklist refusals (survey-checklist spec 7.3 and its submission spec). Not an advice:
 * {@link SurveyExceptionHandler} is the one survey advice and keeps every {@code @ExceptionHandler} method in its
 * order, so Spring picks the same handler as before (clean-up plan amendment 2026-11-08, SV-272); each method here
 * answers for the handler of the same name.
 */
final class ChecklistExceptionHandler {

    private ChecklistExceptionHandler() {
    }

    /** survey-checklist spec 5.5: every rule a checklist body breaks, each on its JSON path. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleChecklistValidation(ChecklistValidationException e) {
        return validationFailed(e.violations().stream()
                .map(violation -> ValidationError.of(violation.path(), violation.message()))
                .toList());
    }

    /** survey-checklist spec 7.3: a missing checklist or checkpoint answers like one of a hidden lead. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleChecklistNotFound(ChecklistNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, e.getMessage(), List.of()));
    }

    /** survey-checklist spec 7.3: the visit, the intent, the lead status or the holder does not allow the command. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleChecklistNotAllowed(ChecklistNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.CHECKLIST_NOT_ALLOWED, e.getMessage(),
                        List.of(ValidationError.withAllowed(e.getField(), e.getMessage(), e.getAllowedValues()))));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleChecklistAlreadyStarted(
            ChecklistAlreadyStartedException e) {
        return conflict(ErrorCode.CHECKLIST_ALREADY_STARTED, e.getMessage());
    }

    /** survey-checklist spec 4.4: the reason is on the next read ({@code readOnlyReason}). */
    static ResponseEntity<ErrorResponse<ValidationError>> handleChecklistReadOnly(ChecklistReadOnlyException e) {
        return conflict(ErrorCode.CHECKLIST_READ_ONLY, e.getMessage());
    }

    /** Gate S2 (survey-checklist submission spec 7.3): uploads or submission are off. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleSubmissionNotAvailable(
            ChecklistSubmissionNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                ErrorCode.CHECKLIST_SUBMISSION_NOT_AVAILABLE, SUBMISSION_NOT_AVAILABLE_MESSAGE,
                List.of(ValidationError.of("submission", SUBMISSION_NOT_AVAILABLE_MESSAGE))));
    }

    /** Gates S3..S7 (survey-checklist submission spec 7.3): one entry per failing gate; the read shows the details. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleNotReady(ChecklistNotReadyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.CHECKLIST_NOT_READY,
                NOT_READY_MESSAGE, e.fields().stream().map(field -> ValidationError.of(field, NOT_READY_MESSAGE))
                        .toList()));
    }

    /** survey-checklist submission spec 7.3: the caller is in the excluded set, or cannot sign in. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleReviewNotAllowed(ChecklistReviewNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                ErrorCode.CHECKLIST_REVIEW_NOT_ALLOWED, REVIEW_NOT_ALLOWED_MESSAGE,
                List.of(ValidationError.of("reviewer", REVIEW_NOT_ALLOWED_MESSAGE))));
    }

    /** survey-checklist submission spec 7.3: one entry per failed rule; the read shows which PASSED rules fail now. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleDecisionNotAllowed(
            ChecklistDecisionNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                ErrorCode.CHECKLIST_DECISION_NOT_ALLOWED, DECISION_NOT_ALLOWED_MESSAGE, e.fields().stream()
                .map(field -> ValidationError.of(field, DECISION_NOT_ALLOWED_MESSAGE)).toList()));
    }

    /** plan PD-8: a SUBMITTED revision of the cycle, or its unmarked PASSED, was committed first. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleSubmittedConflict(
            ChecklistSubmittedConflictException e) {
        return handleStaleVersion(new SurveyConcurrentModificationException("expectedVersion"));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleChecklistTakeoverRequired(
            ChecklistTakeoverRequiredException e) {
        return conflict(ErrorCode.CHECKLIST_TAKEOVER_REQUIRED, e.getMessage());
    }
}
