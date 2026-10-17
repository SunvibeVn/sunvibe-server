package vn.sunvibe.shared.dto.response.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import vn.sunvibe.shared.logging.CorrelationId;

/**
 * Error body. {@code code} and {@code correlationId} are additive (ADR-010): a body built the old way leaves them
 * null and so serializes exactly as before.
 */
@Builder
@JsonPropertyOrder({"code", "message", "errors", "correlationId"})
public record ErrorResponse<E>(
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        String message,
        List<E> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String correlationId
) {

    public ErrorResponse(String message, List<E> errors) {
        this(null, message, errors, null);
    }

    /** New-style body: carries the stable code and the correlation id of the current request. */
    public static <E> ErrorResponse<E> of(String code, String message, List<E> errors) {
        return new ErrorResponse<>(code, message, errors, CorrelationId.current());
    }
}
