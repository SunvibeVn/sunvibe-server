package vn.sunvibe.auth.core.application.command.auth;

/**
 * Ask for a password-reset code by SMS.
 *
 * @param phoneNumber the account's phone number (its username)
 */
public record PasswordForgotSmsCommand(String phoneNumber) {
}
