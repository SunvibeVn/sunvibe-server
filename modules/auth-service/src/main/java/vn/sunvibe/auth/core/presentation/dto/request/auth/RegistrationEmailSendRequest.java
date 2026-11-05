package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotNull;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Body of {@code POST /api/v1/auth/registration/email/send-code}.
 */
public record RegistrationEmailSendRequest(
        @NotNull(message = "Email is required.")
        @ValidEmail
        String email
) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "RegistrationEmailSendRequest[hidden]";
    }
}
