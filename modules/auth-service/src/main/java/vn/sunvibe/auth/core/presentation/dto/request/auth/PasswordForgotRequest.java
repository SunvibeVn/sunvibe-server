package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Body of {@code POST /api/v1/auth/password-reset/email}.
 */
@Builder
public record PasswordForgotRequest(
        @NotBlank(message = "Email is required.")
        @ValidEmail
        String email
) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "PasswordForgotRequest[hidden]";
    }
}
