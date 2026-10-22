package vn.sunvibe.acquisition.core.presentation.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.exc.MismatchedInputException;
import vn.sunvibe.acquisition.core.application.exception.AdministrativeAreaNotFoundException;
import vn.sunvibe.acquisition.core.application.exception.ConcurrentLeadModificationException;
import vn.sunvibe.acquisition.core.application.exception.IdempotencyKeyReusedException;
import vn.sunvibe.acquisition.core.application.exception.IneligibleAssigneeException;
import vn.sunvibe.acquisition.core.application.exception.IntakeValidationException;
import vn.sunvibe.acquisition.core.application.exception.OwnerLeadNotFoundException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.exception.ResourceNotFoundException;
import vn.sunvibe.shared.exception.ValidationException;
import vn.sunvibe.shared.logging.CorrelationId;

import java.lang.reflect.Method;
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

/**
 * ADR-010 error bodies of the acquisition endpoints (acquisition-owner-intake spec 6.5, acquisition-owner-lead-queue
 * spec 6): every body has a stable
 * code, the failing fields ({@code []} when none) and the correlation id. Messages are English and never contain a
 * submitted value. 401 and 403 stay with Spring Security: its exceptions are rethrown.
 */
@RestControllerAdvice(basePackages = "vn.sunvibe.acquisition.core.presentation")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AcquisitionExceptionHandler {

    static final String VALIDATION_FAILED_MESSAGE = "Validation failed.";
    static final String MALFORMED_REQUEST_MESSAGE = "Request body could not be read.";
    /** Also the message of the 415 that OwnerIntakeBodyLimitFilter gives before Spring MVC runs. */
    public static final String REQUEST_NOT_PROCESSED_MESSAGE = "The request cannot be processed.";
    static final String INTERNAL_ERROR_MESSAGE = "Unexpected server error.";
    private static final String INVALID_VALUE_MESSAGE = "Value is not valid for this field.";
    static final String LEAD_NOT_FOUND_MESSAGE = "Owner lead not found.";
    /** Also the message of the 404 that OwnerIntakeEnabledFilter gives while the intake form is switched off. */
    public static final String NOT_FOUND_MESSAGE = "Resource not found.";
    static final String INVALID_TRANSITION_MESSAGE = "The lead's status does not allow this change.";
    static final String STATUS_CHANGE_NOT_ALLOWED_MESSAGE =
            "This status change is not allowed from the current status.";
    static final String LEAD_CLOSED_MESSAGE = "The lead is closed and cannot be changed.";
    static final String CONCURRENT_MODIFICATION_MESSAGE =
            "The lead was changed by someone else. Reload it and try again.";
    static final String INELIGIBLE_ASSIGNEE_MESSAGE = "The user cannot be given owner leads.";
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int MAX_EXCEPTIONS_CHECKED = 50;

    // ==================== 400 VALIDATION_FAILED ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        return validationFailed(fieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /** Refused by the service or the domain: unknown ward, bad phone, wrong unit, old notice version, ... */
    @ExceptionHandler({IntakeValidationException.class, InvalidIntakeValueException.class})
    public ResponseEntity<ErrorResponse<ValidationError>> handleRefusedValue(ValidationException e) {
        return validationFailed(List.of(ValidationError.of(e.getFieldName(), e.getMessage())));
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

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleMissingHeader(MissingRequestHeaderException e) {
        return validationFailed(List.of(ValidationError.of(e.getHeaderName(), "Header is required.")));
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
            errors.add(ValidationError.of(field(violation), violation.getMessage()));
        }
        return validationFailed(errors);
    }

    /**
     * A body over the size limit is 413; a value Jackson could not convert (bad enum, date or number) is a
     * validation error on its JSON path, for example {@code askingPrice.unit}; anything else is unreadable JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadableBody(HttpMessageNotReadableException e) {
        if (findCause(e, PayloadTooLargeException.class) != null) {
            return payloadTooLarge();
        }
        MismatchedInputException mismatch = findCause(e, MismatchedInputException.class);
        if (mismatch != null && !mismatch.getPath().isEmpty()) {
            return validationFailed(List.of(ValidationError.of(jsonPath(mismatch), INVALID_VALUE_MESSAGE)));
        }
        // A number out of range for its target (beyond int for an enum, beyond long for expectedVersion) fails as a
        // coercion error before the value is looked up or bound: a validation error on its JSON path like any bad number.
        InputCoercionException coercion = findCause(e, InputCoercionException.class);
        if (coercion != null && !coercion.getPath().isEmpty()) {
            return validationFailed(List.of(ValidationError.of(jsonPath(coercion), INVALID_VALUE_MESSAGE)));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_MESSAGE, List.of()));
    }

    // ==================== 413, 409 ====================

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handlePayloadTooLarge(PayloadTooLargeException e) {
        return payloadTooLarge();
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleKeyReused(IdempotencyKeyReusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.IDEMPOTENCY_KEY_REUSED, e.getMessage(), List.of()));
    }

    // ==================== Staff lead and territory endpoints ====================

    /**
     * A value of a lead command or list parameter breaks a domain rule: a missing close reason, a bad duplicate
     * target, the assignee filter outside scope all, ... (a malformed request code is an intake value, see
     * {@code handleRefusedValue}).
     */
    @ExceptionHandler(InvalidLeadCommandException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidLeadCommand(InvalidLeadCommandException e) {
        return validationFailed(List.of(ValidationError.of(e.getField(), e.getMessage())));
    }

    /**
     * acquisition-owner-lead-queue BR-2: a lead the caller may not see gets the same body as a lead that does not exist,
     * so the answer never tells whether a hidden lead exists. The message is fixed and names no id.
     */
    @ExceptionHandler(OwnerLeadNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleLeadNotFound(OwnerLeadNotFoundException e) {
        return notFound(LEAD_NOT_FOUND_MESSAGE);
    }

    /** An unknown province or ward of a territory request; any other missing resource gets a generic message. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(ResourceNotFoundException e) {
        return notFound(e instanceof AdministrativeAreaNotFoundException ? e.getMessage() : NOT_FOUND_MESSAGE);
    }

    /**
     * acquisition module brief BR-3: the lead's status does not allow the change. The error names {@code toStatus}
     * for a status change and {@code status} for an assignment or a note on a closed lead, and lists the statuses
     * the lead may move to now ({@code []} when none). The field message is fixed: the exception's own message
     * names the requested status, a submitted value.
     */
    @ExceptionHandler(InvalidLeadTransitionException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidTransition(InvalidLeadTransitionException e) {
        ValidationError error = e.getTo() == null
                ? ValidationError.withAllowed("status", LEAD_CLOSED_MESSAGE, List.of())
                : ValidationError.withAllowed("toStatus", STATUS_CHANGE_NOT_ALLOWED_MESSAGE,
                        e.getAllowed().stream().map(Enum::name).toList());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.INVALID_STATE_TRANSITION, INVALID_TRANSITION_MESSAGE,
                        List.of(error)));
    }

    /**
     * acquisition-owner-lead-queue BR-9: the lead changed since the caller loaded it, seen either by the version the
     * caller sent or by the row version when two changes are saved at the same time. Nothing was changed. The error
     * names the request field that carried the version; a note sends none, so its body has no field.
     */
    @ExceptionHandler(ConcurrentLeadModificationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleConcurrentLeadModification(
            ConcurrentLeadModificationException e) {
        return e.getField() == null ? concurrentModification(List.of()) : concurrentModification(
                List.of(ValidationError.of(e.getField(), CONCURRENT_MODIFICATION_MESSAGE)));
    }

    /** A row-version conflict that reached the handler unmapped, from an assignment or a status change. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse<ValidationError>> handleConcurrentModification(Exception e) {
        return concurrentModification();
    }

    /** The person cannot be given owner leads or a ward; the error names the field that named them. */
    @ExceptionHandler(IneligibleAssigneeException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleIneligibleAssignee(IneligibleAssigneeException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ErrorResponse.of(ErrorCode.INELIGIBLE_ASSIGNEE, INELIGIBLE_ASSIGNEE_MESSAGE,
                        List.of(ValidationError.of(e.getField(), e.getMessage()))));
    }

    // ==================== Everything else ====================

    /**
     * Spring Security exceptions are rethrown so its own 401/403 bodies are kept (also the 403 of the scope check
     * of the lead list). An optimistic-lock failure wrapped by the transaction commit is the 409 of
     * {@link #handleConcurrentModification}. Another Spring MVC error (for example 415 for a body that is not JSON)
     * keeps its status and headers, with {@code MALFORMED_REQUEST}. Anything else is a 500 whose details stay out of
     * the response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (findCause(e, OptimisticLockException.class) != null
                || findCause(e, ObjectOptimisticLockingFailureException.class) != null) {
            return concurrentModification();
        }
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .headers(springError.getHeaders())
                    .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, REQUEST_NOT_PROCESSED_MESSAGE, List.of()));
        }
        if (carriesRequestData(e)) {
            // The message and the stack trace (with its causes) can quote submitted values, e.g. a SQL key
            log.error("Unexpected error in acquisition: {} (correlationId={})",
                    e.getClass().getName(), CorrelationId.current());
        } else {
            log.error("Unexpected error in acquisition: {} (correlationId={})",
                    e.getClass().getName(), CorrelationId.current(), e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE, List.of()));
    }

    // ==================== Helpers ====================

    private static ResponseEntity<ErrorResponse<ValidationError>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, message, List.of()));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> concurrentModification() {
        return concurrentModification(
                List.of(ValidationError.of("expectedVersion", CONCURRENT_MODIFICATION_MESSAGE)));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> concurrentModification(List<ValidationError> errors) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION, CONCURRENT_MODIFICATION_MESSAGE, errors));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> payloadTooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of(ErrorCode.PAYLOAD_TOO_LARGE, PayloadTooLargeException.MESSAGE, List.of()));
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

    private static List<ValidationError> fieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> ValidationError.of(error.getField(),
                        error.getDefaultMessage() == null ? INVALID_VALUE_MESSAGE : error.getDefaultMessage()))
                .toList();
    }

    /** The name the client used: the header, query or path name when the parameter has one. */
    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null) {
            return firstNonEmpty(header.name(), header.value(), parameter.getParameterName());
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null) {
            return firstNonEmpty(param.name(), param.value(), parameter.getParameterName());
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            return firstNonEmpty(path.name(), path.value(), parameter.getParameterName());
        }
        return firstNonEmpty(parameter.getParameterName(), "request", null);
    }

    private static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return third;
    }

    /**
     * Bean properties of the path joined by dots ({@code askingPrice.amount}); a method parameter by the name the
     * client used (its query, path or header name), since the modules compile without {@code -parameters}; else
     * the name the validator gives it.
     */
    private static String field(ConstraintViolation<?> violation) {
        List<String> properties = new ArrayList<>();
        String last = null;
        Path.MethodNode method = null;
        Path.ParameterNode parameter = null;
        for (Path.Node node : violation.getPropertyPath()) {
            last = node.getName();
            switch (node.getKind()) {
                case PROPERTY -> properties.add(node.getName());
                case METHOD -> method = node.as(Path.MethodNode.class);
                case PARAMETER -> parameter = node.as(Path.ParameterNode.class);
                default -> {
                    // container elements, return values and beans add nothing to the field name
                }
            }
        }
        if (!properties.isEmpty()) {
            return String.join(".", properties);
        }
        if (method != null && parameter != null && violation.getRootBeanClass() != null) {
            Method target = ReflectionUtils.findMethod(ClassUtils.getUserClass(violation.getRootBeanClass()),
                    method.getName(), method.getParameterTypes().toArray(Class<?>[]::new));
            if (target != null) {
                MethodParameter methodParameter = new MethodParameter(target, parameter.getParameterIndex());
                if (methodParameter.hasParameterAnnotation(RequestParam.class)
                        || methodParameter.hasParameterAnnotation(PathVariable.class)
                        || methodParameter.hasParameterAnnotation(RequestHeader.class)) {
                    return parameterName(methodParameter);
                }
            }
        }
        return last;
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
     * True when the exception, one of its causes or one of the exceptions suppressed along the way (a failed
     * rollback or close) may quote request data in its message: database errors (SQL keys and values), JSON errors
     * and validation errors. A stack trace prints all of them, so all of them are checked.
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
                    || current instanceof JacksonException || current instanceof jakarta.validation.ValidationException
                    || current instanceof ValidationException) {
                return true;
            }
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            pending.addAll(List.of(current.getSuppressed()));
        }
        // Too many to check: assume the worst and keep the stack trace out
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
