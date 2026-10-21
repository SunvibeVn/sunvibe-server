package vn.sunvibe.config;

import jakarta.mail.AuthenticationFailedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.shared.constant.error.SystemErrorMessage;
import vn.sunvibe.shared.dto.response.error.BaseErrorModel;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.exception.ApplicationException;
import vn.sunvibe.shared.exception.BaseBusinessException;
import vn.sunvibe.shared.exception.ValidationException;
import vn.sunvibe.shared.time.AppClock;

import java.util.List;

@RestControllerAdvice
@Log4j2
public class GlobalServerExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<java.util.Map<String, String>> handleAccessDenied(AccessDeniedException e,
            jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of(
                "error", "Forbidden",
                "message", "You do not have permission to access this resource.",
                "path", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseErrorModel> handleMalformedRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new BaseErrorModel(HttpStatus.BAD_REQUEST.value(), SystemErrorMessage.BAD_REQUEST,
                        AppClock.currentUTC()));
    }

    // Handling unknown system errors (500). An error Spring raises with a client status of its own (no route 404,
    // wrong method 405, body type 415, missing parameter 400, ...) keeps that status and its headers (Allow, Accept):
    // it is the caller's mistake, not a server failure. The body keeps the BaseErrorModel shape (ADR-010)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseErrorModel> handleGlobalException(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            int status = springError.getStatusCode().value();
            // 404 is mostly scanners and typos: kept out of the warning log
            if (status == HttpStatus.NOT_FOUND.value()) {
                log.debug("Request refused by the framework: {} ({})", e.getClass().getSimpleName(), status);
            } else {
                log.warn("Request refused by the framework: {} ({})", e.getClass().getSimpleName(), status);
            }
            return ResponseEntity.status(status)
                    .headers(springError.getHeaders())
                    .body(new BaseErrorModel(status, clientErrorMessage(status), AppClock.currentUTC()));
        }
        log.error("Unhandled System Error: ", e);

        BaseErrorModel errorResponse = new BaseErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), SystemErrorMessage.INTERNAL_ERROR, AppClock.currentUTC());
        log.error(e.getClass());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // Handling business logic errors (e.g., BusinessException). The ApplicationException, AuthenticationFailedException
    // and BaseBusinessException handlers answer with a fixed message code and log only the exception classes: the text
    // of an exception or of its cause can name a user, a phone number or a SQL detail
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<BaseErrorModel> handleBusinessException(ApplicationException e) {
        log.error("Business logic violation: {}", exceptionClasses(e));

        BaseErrorModel errorResponse = new BaseErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), SystemErrorMessage.INTERNAL_ERROR, AppClock.currentUTC());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<BaseErrorModel> handleInternalAuthException(AuthenticationFailedException e) {
        log.error("Authentication service error: {}", exceptionClasses(e));

        BaseErrorModel errorResponse = new BaseErrorModel(HttpStatus.UNAUTHORIZED.value(), SystemErrorMessage.UNAUTHORIZED, AppClock.currentUTC());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // Handling Validate dto data (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleValidationException(MethodArgumentNotValidException e) {

        // Get validation error list
        List<ValidationError> errors = e.getBindingResult().getFieldErrors().stream().map(error -> ValidationError.builder().field(error.getField()).message(error.getDefaultMessage()).build()).toList();

        // Build error response to return to client
        ErrorResponse<ValidationError> errorResponse = ErrorResponse.<ValidationError>builder().message(SystemErrorMessage.VALIDATION_FAILED).errors(errors).build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleBusinessErrors(ValidationException ex) {
        List<ValidationError> errors = List.of(new ValidationError(ex.getFieldName(), ex.getMessage()));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.<ValidationError>builder().message(SystemErrorMessage.VALIDATION_FAILED).errors(errors).build());
    }

    // Handling user not found exception
    @ExceptionHandler(value = BaseBusinessException.class)
    public ResponseEntity<BaseErrorModel> handleUserNotFoundException(BaseBusinessException e) {

        log.warn("Business exception answered 404: {}", exceptionClasses(e));
        BaseErrorModel errorMessage = new BaseErrorModel(HttpStatus.NOT_FOUND.value(), SystemErrorMessage.RESOURCE_NOT_FOUND, AppClock.currentUTC());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage);
    }

    /** The class of an exception and of its cause, without their messages (no PII in logs). */
    private static String exceptionClasses(Throwable e) {
        Throwable cause = e.getCause();
        return cause == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + " caused by " + cause.getClass().getSimpleName();
    }

    /** The message code of a framework client error: the existing codes for 404 and 400, else the status name. */
    private static String clientErrorMessage(int status) {
        if (status == HttpStatus.NOT_FOUND.value()) {
            return SystemErrorMessage.RESOURCE_NOT_FOUND;
        }
        HttpStatus known = HttpStatus.resolve(status);
        if (status == HttpStatus.BAD_REQUEST.value() || known == null) {
            return SystemErrorMessage.BAD_REQUEST;
        }
        return known.name();
    }
}
