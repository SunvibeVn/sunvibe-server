package vn.sunvibe.brokerage.core.presentation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.brokerage.core.application.exception.BrokerageNotFoundException;
import vn.sunvibe.brokerage.core.presentation.controller.BrokerageEligibilityController;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;

/** ADR-010 error bodies of the brokerage agreement endpoints (E05-S1 spec 4, plan decision E05S1-P5). */
@RestControllerAdvice(basePackageClasses = BrokerageEligibilityController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class BrokerageExceptionHandler {

    static final String REQUEST_NOT_PROCESSED_MESSAGE = "The request cannot be processed.";

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
