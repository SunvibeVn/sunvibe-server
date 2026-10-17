package vn.sunvibe.property.core.presentation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.sunvibe.property.core.domain.exception.ProvinceNotFoundException;
import vn.sunvibe.property.core.presentation.controller.area.AdministrativeAreaController;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.shared.dto.response.error.ValidationError;
import vn.sunvibe.shared.logging.CorrelationId;

import java.util.List;

/** ADR-010 error bodies of the public catalog endpoints only (the property endpoints keep their own handler). */
@RestControllerAdvice(basePackageClasses = AdministrativeAreaController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AdministrativeAreaExceptionHandler {

    static final String REQUEST_NOT_PROCESSED_MESSAGE = "The request cannot be processed.";

    @ExceptionHandler(ProvinceNotFoundException.class)
    public ResponseEntity<ErrorResponse<ValidationError>> handleNotFound(ProvinceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, "Province was not found.", List.of()));
    }

    /**
     * Same rules as the acquisition handler (acquisition-owner-intake spec 5.2, 6.5): Spring Security exceptions are
     * rethrown so it keeps its own 401/403 bodies; another Spring MVC error (for example 415 for a body that is not
     * JSON) keeps its status and headers, with {@code MALFORMED_REQUEST}. Anything else is a server error whose
     * details stay out of the response. Its log line has the exception class and the correlation id only: no
     * message and no stack trace, because those can carry request data.
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
        log.error("Unexpected error in the administrative area catalog: {} (correlationId={})",
                e.getClass().getName(), CorrelationId.current());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error.", List.of()));
    }
}
