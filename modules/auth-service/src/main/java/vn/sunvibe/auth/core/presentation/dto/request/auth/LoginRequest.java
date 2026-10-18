package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /api/v1/auth/login}; the username is the phone number.
 */
public record LoginRequest(

        @NotNull(message = "Phone numbers is mandatory.")
        @Pattern(
                regexp = "^[0-9]{10,12}$",
                message = "Phone numbers must be between 10 and 12 digits."
        )
        String username,

        @NotNull(message = "Password is mandatory.")
        String password

) {
}