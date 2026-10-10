package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/auth/forgot-password/sms}.
 */
public record PasswordForgotSmsRequest(
        @NotBlank(message = "Phone number is required.")
        @Pattern(regexp = "^[0-9]{10,12}$", message = "Phone numbers must be between 10 and 12 digits.")
        String phoneNumber
) {
}
