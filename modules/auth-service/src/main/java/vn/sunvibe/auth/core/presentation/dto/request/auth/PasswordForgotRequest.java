package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record PasswordForgotRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email is invalid.")
        String email
) {
}