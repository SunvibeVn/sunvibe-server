package vn.sunvibe.property.core.presentation.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.domain.exception.IntakeConflictException;
import vn.sunvibe.property.core.domain.exception.IntakeStateException;
import vn.sunvibe.property.core.presentation.controller.intake.StaffInventoryIntakeController;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;

import java.util.List;

/**
 * The ADR-010 errors of the intake controllers (property-create intake spec 5.1, 5.4, 7.4). A lock or serialization failure
 * is a conflict, never a 500. No message quotes a request or a row.
 */
@RestControllerAdvice(basePackageClasses = StaffInventoryIntakeController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IntakeExceptionHandler {

    private static final String GATE_MESSAGE = "The intake gate is closed for this reason.";
    private static final String FIELD_MESSAGE = "This value is not accepted.";

    @ExceptionHandler(IntakeRefusedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleRefusal(IntakeRefusedException e) {
        IntakeError error = e.error();
        List<ValidationError> entries = error == IntakeError.INTAKE_GATE_FAILED
                ? e.fieldErrors().stream().map(reason -> ValidationError.of("gate." + reason, GATE_MESSAGE)).toList()
                : e.fieldErrors().stream().map(field -> ValidationError.of(field, FIELD_MESSAGE)).toList();
        return ResponseEntity.status(error.status()).body(ErrorResponse.of(error.name(), error.message(), entries));
    }

    @ExceptionHandler(IntakeStateException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleState(IntakeStateException e) {
        return body(IntakeError.INTAKE_STATE);
    }

    @ExceptionHandler({IntakeConflictException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ErrorResponse<ValidationError>> handleConflict(Exception e) {
        return body(IntakeError.CONCURRENT_MODIFICATION);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalid(MethodArgumentNotValidException e) {
        List<ValidationError> entries = e.getBindingResult().getFieldErrors().stream()
                .map(field -> ValidationError.of(field.getField(), FIELD_MESSAGE)).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                IntakeError.VALIDATION_FAILED.message(), entries));
    }

    /** The name of the parameter only: the value is the caller's and stays out of the answer and the log. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                IntakeError.VALIDATION_FAILED.message(), List.of(ValidationError.of(e.getName(), FIELD_MESSAGE))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST,
                "The request could not be read.", List.of()));
    }

    private static ResponseEntity<ErrorResponse<ValidationError>> body(IntakeError error) {
        return ResponseEntity.status(error.status()).body(ErrorResponse.of(error.name(), error.message(), List.of()));
    }
}
