package vn.sunvibe.auth.core.application.command.auth;

/**
 * Step 2 of the password reset: prove control of the e-mail/phone and set a new password.
 *
 * @param resetToken  the token from step 1 (e-mail link or SMS-flow response)
 * @param otpCode     the 6-digit code that was sent
 * @param newPassword the new raw password, already validated for strength
 */
public record PasswordResetCommand(String resetToken, String otpCode, String newPassword) {
}
