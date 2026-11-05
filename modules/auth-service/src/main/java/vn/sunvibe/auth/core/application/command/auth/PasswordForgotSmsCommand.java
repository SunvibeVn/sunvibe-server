package vn.sunvibe.auth.core.application.command.auth;

/**
 * Ask for a password-reset code by SMS.
 *
 * @param phoneNumber the account's phone number (its username)
 */
public record PasswordForgotSmsCommand(String phoneNumber) {

    /** Contacts and secrets stay out of logs (auth-email-access 4.10). */
    @Override
    public String toString() {
        return "PasswordForgotSmsCommand[hidden]";
    }
}
