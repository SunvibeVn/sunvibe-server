package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Result of a successful password reset.
 *
 * @param passwordChangedAt server-local time of the change
 */
@Builder
public record PasswordResetResponse(
        String message,
        LocalDateTime passwordChangedAt
) {
}