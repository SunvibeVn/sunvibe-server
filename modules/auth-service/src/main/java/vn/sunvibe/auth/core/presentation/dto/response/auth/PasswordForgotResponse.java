package vn.sunvibe.auth.core.presentation.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Same shape whether or not the e-mail/phone belongs to an account.
 *
 * @param resetToken SMS flow only: send it back with the SMS code to {@code /reset-password}.
 *                   For the e-mail flow it is absent (the token travels in the e-mail).
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordForgotResponse(
        String message,
        String resetToken,
        Instant expiresAt,
        long otpExpirationMinutes
) {
}
