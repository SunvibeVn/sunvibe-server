package vn.sunvibe.auth.core.presentation.dto.request.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ConfirmPasswordMatch;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.StrongPassword;

/**
 * Body of {@code POST /api/users/command/register}.
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
        @Pattern(
                regexp = "^[0-9]{10,12}$",
                message = "Phone numbers must be between 10 and 12 digits."
        )
        String username,

        // Optional: empty means "not provided"; format is validated by the Email domain VO.
        @Size(max = 100, message = "At most 100 characters in length.")
        String email,

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
        String confirmPassword

) {
}