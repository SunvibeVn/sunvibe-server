package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Body of {@code POST /api/v1/me/email/send-code}.
 */
public record EmailBindSendRequest(
        @NotNull(message = "Email is required.")
        @ValidEmail
        String newEmail,

        @NotBlank(message = "Current password is required.")
        @Size(min = 1, max = 256, message = "From 1 to 256 characters in length.")
        String currentPassword
) {

    /** The password and the address stay out of logs. */
    @Override
    public String toString() {
        return "EmailBindSendRequest[hidden]";
    }
}
