package vn.sunvibe.brokerage.core.presentation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.sunvibe.brokerage.core.application.exception.AgreementRefusedException;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementExistsException;
import vn.sunvibe.brokerage.core.domain.exception.AgreementPeriodException;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.presentation.controller.BrokerageEligibilityController;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ADR-010 error bodies of the brokerage agreement endpoints: the eligibility read (E05-S1 spec 4, plan decision
 * E05S1-P5), the agreement create and detail (E05-S2 spec 4, plan decision E05S2-P2), and the agreement list and history
 * with their query parameters (E05-S7 spec S7-D13). No message quotes a value of the request or of a row; an entry names
 * a field, a parameter or a reason group only. The class runs before the global handler, and within it the most specific
 * exception type wins over the catch-all.
 */
@RestControllerAdvice(basePackageClasses = BrokerageEligibilityController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class BrokerageExceptionHandler {

    static final String REQUEST_NOT_PROCESSED_MESSAGE = "The request cannot be processed.";
    static final String FIELD_MESSAGE = "This value is not accepted.";
    static final String VALIDATION_FAILED_MESSAGE = "Validation failed.";
    private static final String TRANSACTION_TYPE_FIELD = "transactionType";
    private static final List<String> TRANSACTION_TYPES = Arrays.stream(TransactionType.values()).map(Enum::name)
            .toList();

    /** A refused agreement command: the reason's status, code and message, and its entries. */
    @ExceptionHandler(AgreementRefusedException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleRefused(AgreementRefusedException e) {
        AgreementRefusedException.Reason reason = e.reason();
        List<ValidationError> entries = e.entries().stream()
                .map(entry -> entry.allowedValuesOrNull() == null
                        ? ValidationError.of(entry.field(), reason.entryMessageOrNull())
                        : ValidationError.withAllowed(entry.field(), reason.entryMessageOrNull(), entry.allowedValuesOrNull()))
                .toList();
        return ResponseEntity.status(reason.status()).body(ErrorResponse.of(reason.name(), reason.message(), entries));
    }

    /** brokerage-agreement BR-6: the period is refused on the field it is about, without its value. */
    @ExceptionHandler(AgreementPeriodException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handlePeriod(AgreementPeriodException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED_MESSAGE, List.of(ValidationError.of(e.field(), FIELD_MESSAGE))));
    }

    /**
     * brokerage-agreement BR-8, BR-9: a draft's content value is refused on its field path (E05-S3 spec S3-D7, S3-D8),
     * without its value.
     */
    @ExceptionHandler(AgreementContentException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleContent(AgreementContentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED_MESSAGE, List.of(ValidationError.of(e.field(), FIELD_MESSAGE))));
    }

    /**
     * A request body that breaks its bean constraints: one entry per field, in the order of the field errors, the first
     * of a field kept; the transaction type's entry lists the accepted values (E05-S2 spec S2-D5).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleInvalidBody(MethodArgumentNotValidException e) {
        Set<String> seen = new HashSet<>();
        List<ValidationError> entries = new ArrayList<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            if (seen.add(error.getField())) {
                entries.add(TRANSACTION_TYPE_FIELD.equals(error.getField())
                        ? ValidationError.withAllowed(error.getField(), FIELD_MESSAGE, TRANSACTION_TYPES)
                        : ValidationError.of(error.getField(), FIELD_MESSAGE));
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED_MESSAGE, entries));
    }

    /**
     * A query parameter whose value is not of its type, such as a page that is not an integer or a flag that is not a
     * boolean (E05-S7 spec S7-D13 (3)): the same body as a value out of bounds, one entry naming the parameter, never its
     * value, and no log line.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                VALIDATION_FAILED_MESSAGE, List.of(ValidationError.of(e.getName(), FIELD_MESSAGE))));
    }

    /** A body, or a date in it, that cannot be read; no entry, since its text is the caller's. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST,
                "The request could not be read.", List.of()));
    }

    /**
     * brokerage-agreement BR-1: the unit already has an unfinished agreement of the type, found by the database's index
     * after a concurrent create; it names no lead and no agreement (brokerage-agreement BR-17).
     */
    @ExceptionHandler(AgreementExistsException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleExists(AgreementExistsException e) {
        AgreementRefusedException.Reason reason = AgreementRefusedException.Reason.AGREEMENT_EXISTS;
        return ResponseEntity.status(reason.status()).body(ErrorResponse.of(reason.name(), reason.message(), List.of()));
    }

    /** A lock or serialization failure is a conflict the caller may retry, never a 500. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleConflict(ConcurrencyFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(ErrorCode.CONCURRENT_MODIFICATION,
                "Something changed at the same time. Try again.", List.of()));
    }

    /** A lead that is unknown, malformed or not visible: one answer for all three, naming no lead. */
    @ExceptionHandler(BrokerageNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(BrokerageNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, "The resource was not found.", List.of()));
    }

    /**
     * Same rules as the administrative area handler: Spring Security exceptions are rethrown so the 401 and 403 keep
     * their own bodies; a Spring MVC error with a client status (for example 405 for a wrong method) keeps its status
     * and headers, with {@code MALFORMED_REQUEST} and no log line. Anything else is a server error whose details stay
     * out of the response. Its log line has the exception class and the correlation id only: no message and no stack
     * trace, because those can carry request data.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(springError.getStatusCode())
                    .headers(springError.getHeaders())
                    .body(ErrorResponse.of(ErrorCode.MALFORMED_REQUEST, REQUEST_NOT_PROCESSED_MESSAGE, List.of()));
        }
        log.error("Unexpected error in brokerage agreement eligibility: {} (correlationId={})",
                e.getClass().getName(), CorrelationId.current());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error.", List.of()));
    }
}
