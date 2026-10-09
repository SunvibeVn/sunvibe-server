package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Builder
public record PasswordResetResponse(
        String message,
        String accessToken,
        String refreshToken,
        LocalDateTime passwordChangedAt
) {

}