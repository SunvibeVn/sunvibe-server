package vn.sunvibe.auth.core.application.command.auth;

import lombok.Builder;

/**
 * Ask for a password-reset code by e-mail.
 *
 * @param email the account's e-mail; matched case-insensitively and only if verified
 */
@Builder
public record PasswordForgotCommand(
        String email
) {
}
