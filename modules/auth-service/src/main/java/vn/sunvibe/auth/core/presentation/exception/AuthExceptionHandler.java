package vn.sunvibe.auth.core.presentation.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.auth.core.application.exception.role.InactiveAccountException;
import vn.sunvibe.auth.core.application.exception.role.RoleNotGrantableException;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.shared.constant.error.SystemErrorMessage;
import vn.sunvibe.shared.dto.response.error.BaseErrorModel;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.time.AppClock;

import java.util.List;

/**
 * Error responses of the role admin API ({@code /api/v1/staff/users/{userId}/roles}). Ordered right after the v1 auth
 * handler and ahead of the application-wide handler, which would otherwise answer these exceptions with a 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthExceptionHandler {

    /** Role admin API: the account named in the path does not exist (404). */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<BaseErrorModel> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new BaseErrorModel(HttpStatus.NOT_FOUND.value(), e.getMessage(), AppClock.currentUTC()));
    }

    /** Role admin API: SUPER_ADMIN, an old role or an unknown name (auth-roles E-6). */
    @ExceptionHandler(RoleNotGrantableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleRoleNotGrantable(RoleNotGrantableException e) {
        return validation(HttpStatus.BAD_REQUEST, "role", e.getMessage());
    }

    /** Role admin API: a role cannot be granted to a locked or terminated account (auth-roles E-7). */
    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInactiveAccount(InactiveAccountException e) {
        return validation(HttpStatus.UNPROCESSABLE_ENTITY, "userId", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> validation(HttpStatus status, String field,
            String message) {
        return ResponseEntity.status(status).body(ErrorResponse.<ValidationError>builder()
                .message(SystemErrorMessage.VALIDATION_FAILED)
                .errors(List.of(ValidationError.builder().field(field).message(message).build()))
                .build());
    }
}
