package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ConfirmPasswordMatch;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.StrongPassword;

/**
 * Body of {@code POST /api/v1/me/password/change}.
 */
@Builder
@ConfirmPasswordMatch(
        password = "newPassword",
        confirmPassword = "confirmPassword",
        message = "Confirm password must match password."
)
public record PasswordChangeRequest(

        @NotBlank(message = "Current password is required.")
        @Size(min = 1, max = 256, message = "From 1 to 256 characters in length.")
        String currentPassword,

        @StrongPassword
        @NotBlank(message = "New password is required.")
        String newPassword,

        @NotBlank(message = "Confirm password is required.")
        @Size(min = 1, max = 256, message = "From 1 to 256 characters in length.")
        String confirmPassword

) {


    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "PasswordChangeRequest[hidden]";
    }
}
