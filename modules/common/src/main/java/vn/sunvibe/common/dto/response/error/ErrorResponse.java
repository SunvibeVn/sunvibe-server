package vn.sunvibe.common.dto.response.error;

import lombok.Builder;

import java.util.List;

@Builder
public record ErrorResponse<E>(
        String message,
        List<E> errors
) {
}