package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PasswordResetResponse(
        String message,
        LocalDateTime passwordChangedAt
) {

}