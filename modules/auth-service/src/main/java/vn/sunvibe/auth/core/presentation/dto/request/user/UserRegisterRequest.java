package vn.sunvibe.auth.core.presentation.dto.request.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ConfirmPasswordMatch;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.StrongPassword;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidPhone;

/**
 * Body of {@code POST /api/v1/auth/registration}.
 */
@Builder
@ConfirmPasswordMatch(
        password = "password",
        confirmPassword = "confirmPassword",
        message = "Confirm password must match password."
)
public record UserRegisterRequest(

        @NotNull(message = "Full name is mandatory.")
        @Size(min = 6, max = 100, message = "From 6 to 100 characters in length.")
        String fullName,

        @NotNull(message = "Phone numbers is mandatory.")
        @ValidPhone
        String username,

        // Optional: empty means "not provided".
        @Pattern(
                regexp = "^(|\\d{9}|\\d{12})$",
                message = "ID card number must be a 9-digit CMND or 12-digit CCCD."
        )
        String idCardNumber,

        @StrongPassword
        @NotNull(message = "Password is mandatory.")
        String password,

        @NotNull(message = "Confirm password is mandatory.")
        @Size(max = 256, message = "At most 256 characters in length.")
        String confirmPassword,

        // Issued by POST /api/v1/auth/registration/phone/verify-code; proves the phone number belongs to the registrant.
        @Size(max = 100, message = "At most 100 characters in length.")
        String registrationToken

) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "UserRegisterRequest[hidden]";
    }
}