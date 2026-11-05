package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotNull;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidPhone;

/**
 * Body of {@code POST /api/v1/auth/registration/phone/send-code}.
 */
public record RegistrationPhoneSendRequest(
        @NotNull(message = "Phone numbers is mandatory.")
        @ValidPhone
        String phoneNumber
) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "RegistrationPhoneSendRequest[hidden]";
    }
}
