package vn.sunvibe.auth.core.presentation.exception;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.core.JacksonException;
import vn.sunvibe.auth.core.application.exception.auth.AccountDisabledException;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.exception.auth.OriginNotAllowedException;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.shared.service.SessionCookieService;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.exception.ValidationException;
import vn.sunvibe.shared.logging.CorrelationId;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
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
 * ADR-010 error bodies of the v1 auth endpoints (auth-browser-session spec §3.4): every body has a stable code, a fixed
 * English message that never quotes a submitted value or an exception message, the failing fields ({@code []} when
 * none) and the correlation id. {@link AuthExceptionHandler} comes right after this one and answers the role admin
 * API. Spring Security exceptions are rethrown so the chain's 401/403 bodies stay.
 */
@RestControllerAdvice(basePackages = "vn.sunvibe.auth.core.presentation.controller.v1")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AuthV1ExceptionHandler {

    static final String VALIDATION_FAILED_MESSAGE = "Validation failed.";
    static final String MALFORMED_REQUEST_MESSAGE = "Request body could not be read.";
    static final String REQUEST_NOT_PROCESSED_MESSAGE = "The request cannot be processed.";
    static final String INVALID_CREDENTIALS_MESSAGE = "Phone number or password is incorrect.";
    static final String ACCOUNT_LOCKED_MESSAGE = "Too many failed login attempts. Try again later.";
    static final String ACCOUNT_DISABLED_MESSAGE = "This account cannot be used.";
    static final String SESSION_EXPIRED_MESSAGE = "The session has expired. Sign in again.";
    static final String ORIGIN_NOT_ALLOWED_MESSAGE = "This request is not allowed.";
    static final String CODE_INCORRECT_MESSAGE = "The code is incorrect.";
    static final String CODE_EXPIRED_MESSAGE = "The code has expired. Ask for a new code.";
    static final String CODE_ATTEMPTS_EXCEEDED_MESSAGE = "Too many wrong codes. Ask for a new code.";
    static final String ALREADY_VERIFIED_MESSAGE = "The contact is already verified.";
    static final String NO_EMAIL_MESSAGE = "The account has no e-mail address.";
    static final String REGISTRATION_TOKEN_INVALID_MESSAGE = "The registration has expired. Verify the phone again.";
    static final String RESET_TOKEN_INVALID_MESSAGE = "The password reset has expired. Start again.";
    static final String CODE_RESEND_TOO_SOON_MESSAGE = "A code was sent recently. Try again later.";
    static final String CODE_DAILY_LIMIT_MESSAGE = "Too many codes today. Try again later.";
    static final String NOT_FOUND_MESSAGE = "Account not found.";
    static final String INTERNAL_ERROR_MESSAGE = "Unexpected server error.";
    private static final String INVALID_VALUE_MESSAGE = "Value is not valid for this field.";
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int MAX_EXCEPTIONS_CHECKED = 50;

    private final SessionCookieService sessionCookieService;

    // ==================== 400 VALIDATION_FAILED, MALFORMED_REQUEST ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        return validationFailed(fieldErrors(e.getBindingResult().getFieldErrors()));
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

    /**
     * An account field refused by the service or a value object: phone, e-mail or ID card in use, wrong current
     * password, new password equal to the current one. The field names are today's; the field messages are the
     * fixed English texts the code throws, none of which quotes the submitted value.
     */
    @ExceptionHandler(AuthValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleRefusedField(AuthValidationException e) {
        return validationFailed(List.of(ValidationError.of(e.getFieldName(), e.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_MESSAGE);
    }

    /** A body that is not JSON, refused while reading {@code @RequestBody}: Spring's 415 and headers are kept. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(e.getStatusCode())
                .headers(e.getHeaders())
                .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, MALFORMED_REQUEST_MESSAGE, List.of()));
    }

    // ==================== Login and session ====================

    /** Too many wrong passwords: 423 with the seconds to wait in {@code Retry-After}. */
    @ExceptionHandler(AccountTemporarilyLockedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleTemporarilyLocked(
            AccountTemporarilyLockedException e) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header(HttpHeaders.RETRY_AFTER, retryAfter(e.getRetryAfter()))
                .body(ErrorResponse.of(ErrorCode.ACCOUNT_LOCKED, ACCOUNT_LOCKED_MESSAGE, List.of()));
    }

    /** A password reset completed for a locked or terminated account. */
    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleAccountDisabled(AccountDisabledException e) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_DISABLED, ACCOUNT_DISABLED_MESSAGE);
    }

    /**
     * auth-browser-session S-21: an unknown phone, a wrong password, a deleted account and an account that may not
     * sign in all get the same body, so the answer never tells which one it was.
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleAuthenticationFailed(AuthenticationFailedException e) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
    }

    /** A refresh that cannot succeed also drops the refresh cookie, so the browser stops sending a dead token. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.clear().toString())
                .body(ErrorResponse.of(ErrorCode.SESSION_EXPIRED, SESSION_EXPIRED_MESSAGE, List.of()));
    }

    /** The exception's own message names the rule for the log; the client gets a fixed one. */
    @ExceptionHandler(OriginNotAllowedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleOriginNotAllowed(OriginNotAllowedException e) {
        return error(HttpStatus.FORBIDDEN, ErrorCode.ORIGIN_NOT_ALLOWED, ORIGIN_NOT_ALLOWED_MESSAGE);
    }

    // ==================== Codes and tokens ====================

    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleVerification(VerificationException e) {
        return switch (e.getReason()) {
            case CODE_INCORRECT -> badRequest(ErrorCode.CODE_INCORRECT, CODE_INCORRECT_MESSAGE);
            case CODE_EXPIRED -> badRequest(ErrorCode.CODE_EXPIRED, CODE_EXPIRED_MESSAGE);
            case CODE_ATTEMPTS_EXCEEDED -> badRequest(ErrorCode.CODE_ATTEMPTS_EXCEEDED, CODE_ATTEMPTS_EXCEEDED_MESSAGE);
            case ALREADY_VERIFIED -> badRequest(ErrorCode.ALREADY_VERIFIED, ALREADY_VERIFIED_MESSAGE);
            case NO_EMAIL -> badRequest(ErrorCode.NO_EMAIL, NO_EMAIL_MESSAGE);
            case REGISTRATION_TOKEN_INVALID ->
                    badRequest(ErrorCode.REGISTRATION_TOKEN_INVALID, REGISTRATION_TOKEN_INVALID_MESSAGE);
        };
    }

    /** A wrong code for a still-usable reset token. */
    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidOtp(InvalidOtpException e) {
        return badRequest(ErrorCode.CODE_INCORRECT, CODE_INCORRECT_MESSAGE);
    }

    @ExceptionHandler(PasswordResetTokenException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleResetToken(PasswordResetTokenException e) {
        return switch (e.getReason()) {
            case TOKEN_INVALID -> badRequest(ErrorCode.RESET_TOKEN_INVALID, RESET_TOKEN_INVALID_MESSAGE);
            case ATTEMPTS_EXCEEDED -> badRequest(ErrorCode.CODE_ATTEMPTS_EXCEEDED, CODE_ATTEMPTS_EXCEEDED_MESSAGE);
        };
    }

    /** A new code asked for too soon or too often: 429 with the seconds to wait in {@code Retry-After}. */
    @ExceptionHandler(VerificationThrottledException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleVerificationThrottled(
            VerificationThrottledException e) {
        ErrorResponse<ValidationError> body = switch (e.getReason()) {
            case RESEND_TOO_SOON ->
                    ErrorResponse.of(ErrorCode.CODE_RESEND_TOO_SOON, CODE_RESEND_TOO_SOON_MESSAGE, List.of());
            case DAILY_LIMIT -> ErrorResponse.of(ErrorCode.CODE_DAILY_LIMIT, CODE_DAILY_LIMIT_MESSAGE, List.of());
        };
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfter(e.getRetryAfter()))
                .body(body);
    }

    /** The signed-in account no longer exists. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUserNotFound(UserNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
    }

    // ==================== Everything else ====================

    /**
     * A server failure inside the login check (for example the database is down). Rethrown, the security chain would
     * turn it into a 401 and the login screen would say "wrong password" while the server is down.
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInternalAuthentication(
            InternalAuthenticationServiceException e) {
        return internalError(e);
    }

    /**
     * Spring Security exceptions are rethrown so the chain's {@code SecurityError} bodies are kept. Another Spring
     * MVC error (for example {@code MissingRequestCookieException}) keeps its status and headers, with
     * {@code MALFORMED_REQUEST}. Anything else, a
     * failed e-mail or SMS hand-off included, is a 500 whose details stay out of the response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            return ResponseEntity.status(springError.getStatusCode())
                    .headers(springError.getHeaders())
                    .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, REQUEST_NOT_PROCESSED_MESSAGE, List.of()));
        }
        return internalError(e);
    }

    // ==================== Helpers ====================

    /**
     * The details stay in the log only (auth-browser-session spec §3.4): the stack trace is kept, so an outage inside
     * the login check can be traced. When the exception or one of its causes may quote request data (a phone number,
     * an e-mail address or a SQL key value), only the class names and the correlation id are logged.
     */
    private static ResponseEntity<ErrorResponse<ValidationError>> internalError(Exception e) {
        if (carriesRequestData(e)) {
            log.error("Unexpected error in auth: {} (causes={}, correlationId={})",
                    e.getClass().getName(), causeClasses(e), CorrelationId.current());
        } else {
            log.error("Unexpected error in auth: {} (correlationId={})",
                    e.getClass().getName(), CorrelationId.current(), e);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    /**
     * True when the exception, one of its causes or one of the exceptions suppressed along the way may quote request
     * data in its message: an integrity or data error of the database (key values), a mail hand-off (the address;
     * only mail exceptions are detected), a JSON or validation error. A stack trace prints all of them, so all of them are checked. A
     * connection failure of the database carries no request data and keeps its stack trace.
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
            if (quotesRequestData(current)) {
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

    private static boolean quotesRequestData(Throwable e) {
        if (e instanceof SQLException sql) {
            // SQLState class 22 (data exception) and 23 (integrity constraint) messages quote the values
            String state = sql.getSQLState();
            return state == null || state.startsWith("22") || state.startsWith("23")
                    || sql instanceof SQLIntegrityConstraintViolationException;
        }
        return e instanceof DataIntegrityViolationException || e instanceof MailException
                || e instanceof MessagingException || e instanceof SendingMailFailedException
                || e instanceof JacksonException || e instanceof jakarta.validation.ValidationException
                || e instanceof ValidationException;
    }

    /** The class names of the cause chain, outermost first, without any message. */
    private static String causeClasses(Throwable e) {
        List<String> names = new ArrayList<>();
        Throwable current = e.getCause();
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            names.add(current.getClass().getName());
            current = current.getCause();
        }
        return String.join(" <- ", names);
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> badRequest(String code, String message) {
        return error(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> error(HttpStatus status, String code,
                                                                        String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message, List.of()));
    }

    private static String retryAfter(Duration wait) {
        return String.valueOf(Math.max(1, wait.toSeconds()));
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
}
