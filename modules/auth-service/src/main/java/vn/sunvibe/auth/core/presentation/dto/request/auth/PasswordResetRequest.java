package vn.sunvibe.auth.core.presentation.dto.request.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.StrongPassword;

/**
 * Body of {@code POST /api/v1/auth/password-reset/complete}. The no-args constructor is
 * needed by Jackson to read the request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class PasswordResetRequest {

    @NotBlank(message = "Reset token is required")
    @Size(min = 1, max = 256, message = "From 1 to 256 characters in length.")
    private String resetToken;

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
    private String otpCode;

    @NotBlank(message = "New password is required")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Size(min = 1, max = 256, message = "From 1 to 256 characters in length.")
    private String confirmPassword;

    @AssertTrue(message = "Passwords must match")
    private boolean isPasswordsMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
