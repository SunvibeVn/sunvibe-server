package vn.sunvibe.survey.core.presentation.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.survey.core.application.exception.common.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.common.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static vn.sunvibe.shared.exception.ApiErrorSupport.findCause;
import static vn.sunvibe.shared.exception.ApiErrorSupport.jsonPath;
import static vn.sunvibe.shared.exception.ApiErrorSupport.parameterName;
import static vn.sunvibe.shared.exception.ApiErrorSupport.sortedByField;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.CHECKLIST_CHANGED_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.CONCURRENT_MODIFICATION_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.INVALID_VALUE_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.MALFORMED_REQUEST_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.NOT_FOUND_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.STATE_MESSAGE;
import static vn.sunvibe.survey.core.presentation.exception.SurveyExceptionHandler.VALIDATION_FAILED_MESSAGE;

/**
 * The bodies of the request-shape errors (400), a hidden lead (404), a stale version or lock failure (409), a lead
 * state or survey refusal, an ineligible surveyor, and the error bodies the other areas share. Not an advice:
 * {@link SurveyExceptionHandler} is the one survey advice and keeps every {@code @ExceptionHandler} method in its
 * order, so Spring picks the same handler as before (clean-up plan amendment 2026-11-08, SV-272); each method here
 * answers for the handler of the same name.
 */
final class CommonExceptionHandler {

    private CommonExceptionHandler() {
    }

    /** How Jackson starts the message of a missing {@code @JsonProperty(required = true)} creator property. */
    private static final String MISSING_REQUIRED_PROPERTY = "Missing required creator property";
    /** The version fields of the checklist commands (survey-checklist spec 7.3). */
    private static final String CHECKLIST_VERSION = "expectedVersion";
    private static final String OTHER_GROUP_VERSIONS = "otherGroupVersions.";
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+)]");

    static ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        return validationFailed(fieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /** A value refused by a command or the domain; no field means the request as a whole (e.g. nothing changed). */
    static ResponseEntity<ErrorResponse<ValidationError>> handleInvalidValue(InvalidSurveyValueException e) {
        if (e.getField() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, e.getMessage(), List.of()));
        }
        return validationFailed(List.of(ValidationError.of(e.getField(), e.getMessage())));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validationFailed(List.of(ValidationError.of(e.getName(), "Value has the wrong type.")));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return validationFailed(List.of(ValidationError.of(e.getParameterName(), "Parameter is required.")));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleMethodValidation(HandlerMethodValidationException e) {
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

    static ResponseEntity<ErrorResponse<ValidationError>> handleConstraintViolation(ConstraintViolationException e) {
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
    static ResponseEntity<ErrorResponse<ValidationError>> handleUnreadableBody(HttpMessageNotReadableException e) {
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

    /** survey-schedule BR-1, E-1: an invisible lead or appointment answers like a missing one. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(SurveyLeadNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE, List.of()));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleStaleVersion(SurveyConcurrentModificationException e) {
        return concurrentModification(e.getField());
    }

    /** A row-version conflict of the appointment that reached the handler unmapped. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleOptimisticLock(Exception e) {
        return concurrentModification("expectedAppointmentVersion");
    }

    /**
     * survey-checklist submission spec 5.4: a lock wait, a deadlock victim or a serialization failure that still occurs is
     * a concurrent change, never a 500.
     */
    static ResponseEntity<ErrorResponse<ValidationError>> handleLockFailure(ConcurrencyFailureException e) {
        return concurrentModification("expectedVersion");
    }

    /** survey-schedule spec 7.3: a refused lead move ({@code []}) or a resumption of a lead not on hold. */
    static ResponseEntity<ErrorResponse<ValidationError>> handleLeadState(InvalidSurveyStateException e) {
        return invalidState(e.getAllowedValues());
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleNotAllowed(SurveyNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.SURVEY_NOT_ALLOWED, e.getMessage(),
                        List.of(ValidationError.withAllowed(e.getField(), e.getMessage(), e.getAllowedValues()))));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> handleIneligible(SurveyorIneligibleException e) {
        List<ValidationError> errors = e.getField() == null ? List.of()
                : List.of(ValidationError.of(e.getField(), e.getMessage()));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ErrorResponse.of(ErrorCode.SURVEYOR_INELIGIBLE, e.getMessage(), errors));
    }

    /** A stale version; on a checklist version (survey-checklist spec 7.3) the message says the checklist changed. */
    static ResponseEntity<ErrorResponse<ValidationError>> concurrentModification(String field) {
        String message = CHECKLIST_VERSION.equals(field) || field.startsWith(OTHER_GROUP_VERSIONS)
                ? CHECKLIST_CHANGED_MESSAGE : CONCURRENT_MODIFICATION_MESSAGE;
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION, message,
                        List.of(ValidationError.of(field, message))));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> payloadTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE,
                MediaBodyTooLargeException.MESSAGE, List.of()));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> conflict(String code, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(code, message, List.of()));
    }

    static ResponseEntity<ErrorResponse<ValidationError>> invalidState(List<String> allowed) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.INVALID_STATE_TRANSITION, STATE_MESSAGE,
                        List.of(ValidationError.withAllowed("status", STATE_MESSAGE, allowed))));
    }

    /** One entry per field (the first message), sorted by field so the body does not depend on check order. */
    static ResponseEntity<ErrorResponse<ValidationError>> validationFailed(List<ValidationError> errors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, VALIDATION_FAILED_MESSAGE, sortedByField(errors)));
    }

    /** Bean Validation paths with dotted indexes, like JSON paths: {@code spaces[1].code} is {@code spaces.1.code}. */
    private static List<ValidationError> fieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> ValidationError.of(INDEX.matcher(error.getField()).replaceAll(".$1"),
                        error.getDefaultMessage() == null ? INVALID_VALUE_MESSAGE : error.getDefaultMessage()))
                .toList();
    }
}
