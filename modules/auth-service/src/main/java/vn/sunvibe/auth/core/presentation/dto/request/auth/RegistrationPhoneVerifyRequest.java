package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidPhone;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/v1/auth/registration/phone/verify-code}.
 */
public record RegistrationPhoneVerifyRequest(
        @NotNull(message = "Phone numbers is mandatory.")
        @ValidPhone
        String phoneNumber,

        @NotBlank(message = "Verification code is required.")
        @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits.")
        String code
) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "RegistrationPhoneVerifyRequest[hidden]";
    }
}
