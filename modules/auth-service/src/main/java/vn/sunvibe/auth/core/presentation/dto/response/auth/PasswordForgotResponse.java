package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;

import java.time.Instant;

@Builder
public record PasswordForgotResponse(
        String message,
        Instant expiresAt,
        long otpExpirationMinutes
) {

}