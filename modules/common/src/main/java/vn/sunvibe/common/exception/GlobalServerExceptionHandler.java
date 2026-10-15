package vn.sunvibe.common.exception;

import jakarta.mail.AuthenticationFailedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.common.constant.error.SystemErrorMessage;
import vn.sunvibe.common.dto.response.error.BaseErrorModel;
import vn.sunvibe.common.dto.response.error.ErrorResponse;
import vn.sunvibe.common.dto.response.error.ValidationError;
import vn.sunvibe.common.time.AppClock;

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

    // Handling unknown system errors (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseErrorModel> handleGlobalException(Exception e) {
        log.error("Unhandled System Error: ", e);

        BaseErrorModel errorResponse = new BaseErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), SystemErrorMessage.INTERNAL_ERROR, AppClock.currentUTC());
        log.error(e.getClass());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // Handling business logic errors (e.g., BusinessException)
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<BaseErrorModel> handleBusinessException(ApplicationException e) {
        log.error("Business logic violation: {}", e.getMessage());

        BaseErrorModel errorResponse = new BaseErrorModel(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), AppClock.currentUTC());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<BaseErrorModel> handleInternalAuthException(AuthenticationFailedException e) {
        log.error("Authentication service error: {}", e.getMessage());

        BaseErrorModel errorResponse = new BaseErrorModel(HttpStatus.UNAUTHORIZED.value(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), AppClock.currentUTC());

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

        log.error("User not found: " + e);
        BaseErrorModel errorMessage = new BaseErrorModel(HttpStatus.NOT_FOUND.value(), e.getMessage(), AppClock.currentUTC());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage);
    }

}
