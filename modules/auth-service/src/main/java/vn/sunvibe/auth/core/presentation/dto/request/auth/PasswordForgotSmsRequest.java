package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidPhone;

/**
 * Body of {@code POST /api/v1/auth/password-reset/sms}.
 */
public record PasswordForgotSmsRequest(
        @NotBlank(message = "Phone number is required.")
        @ValidPhone
        String phoneNumber
) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "PasswordForgotSmsRequest[hidden]";
    }
}
