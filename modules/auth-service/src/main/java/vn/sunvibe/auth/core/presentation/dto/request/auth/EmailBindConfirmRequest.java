package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Body of {@code POST /api/v1/me/email/confirm-code}.
 */
public record EmailBindConfirmRequest(
        @NotNull(message = "Email is required.")
        @ValidEmail
        String newEmail,

        @NotBlank(message = "Verification code is required.")
        @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits.")
        String code
) {

    /** The code and the address stay out of logs. */
    @Override
    public String toString() {
        return "EmailBindConfirmRequest[hidden]";
    }
}
