package vn.sunvibe.auth.core.application.usecase.external.email;

/**
 * Sends the auth module's e-mails.
 */
public interface EmailServicePort {

    /**
     * Sends a password-reset code together with its reset token.
     *
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendPasswordResetEmail(String email, String otpCode, String resetToken);

    /** Tells the owner their password was reset. Never throws: the reset already happened. */
    void sendPasswordResetConfirmationEmail(String email);

    /**
     * Sends the 6-digit code that proves the user controls this address.
     *
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendVerificationCodeEmail(String email, String code);
}
