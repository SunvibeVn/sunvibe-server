package vn.sunvibe.common.dto.response.error;

import lombok.Builder;

import java.time.Instant;

@Builder
public record BaseErrorModel(
        int code,
        String message,
        Instant errorTime
) {
}
