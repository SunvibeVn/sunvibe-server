package vn.sunvibe.auth.core.presentation.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ConfirmPasswordMatch;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.StrongPassword;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Body of {@code POST /api/v1/auth/registration/email}.
 */
@ConfirmPasswordMatch(
        password = "password",
        confirmPassword = "confirmPassword",
        message = "Confirm password must match password."
)
public record EmailRegisterRequest(

        @NotNull(message = "Email is required.")
        @ValidEmail
        String email,

        // Issued by POST /api/v1/auth/registration/email/verify-code; proves the mailbox belongs to the registrant.
        @NotBlank(message = "Registration token is required.")
        @Size(max = 100, message = "At most 100 characters in length.")
        String registrationToken,

        // A control character, NUL above all, cannot be stored (SV-087).
        @NotNull(message = "Full name is mandatory.")
        @Size(min = 6, max = 100, message = "From 6 to 100 characters in length.")
        @Pattern(regexp = "^\\P{Cntrl}*$", message = "Full name has an invalid character.")
        String fullName,

        @StrongPassword
        @NotNull(message = "Password is mandatory.")
        String password,

        @NotNull(message = "Confirm password is mandatory.")
        @Size(max = 256, message = "At most 256 characters in length.")
        String confirmPassword

) {

    /** The token and the passwords stay out of logs. */
    @Override
    public String toString() {
        return "EmailRegisterRequest[hidden]";
    }
}
