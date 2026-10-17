package vn.sunvibe.auth.core.presentation.dto.response.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A password reset was started. Same shape whether or not the e-mail or phone belongs to an account.
 * {@code resetToken} is present for the SMS flow only; for the e-mail flow it is omitted (the token
 * travels in the e-mail).
 */
@Schema(description = "A password reset was started; same shape whether or not an account exists")
public record PasswordResetStartResponse(
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "SMS flow only: send it back with the SMS code to reset the password; "
                + "omitted for the e-mail flow")
        String resetToken,
        @Schema(description = "When the code stops working")
        Instant expiresAt,
        @Schema(description = "How many minutes a reset code is valid", example = "5")
        long otpExpirationMinutes) {

    /** The reset token stays out of logs. */
    @Override
    public String toString() {
        return "PasswordResetStartResponse[resetToken=" + (resetToken == null ? "null" : "[hidden]")
                + ", expiresAt=" + expiresAt + ", otpExpirationMinutes=" + otpExpirationMinutes + "]";
    }
}
