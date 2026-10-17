package vn.sunvibe.shared.dto.response.error;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;

@Builder
public record ValidationError(
        String field,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> allowedValues
) {

    public ValidationError(String field, String message) {
        this(field, message, null);
    }

    public static ValidationError of(String field, String message) {
        return new ValidationError(field, message, null);
    }

    /** For an enum-like field: tells the client which values are accepted. */
    public static ValidationError withAllowed(String field, String message, List<String> allowed) {
        return new ValidationError(field, message, allowed);
    }
}
