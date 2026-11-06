package vn.sunvibe.auth.core.application.shared.external.email;

import java.time.Instant;

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

    /**
     * Tells the owner their password was reset.
     *
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendPasswordResetConfirmationEmail(String email);

    /**
     * Sends the code that proves a mailbox before an account exists. The code is never in the subject.
     *
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendRegistrationCodeEmail(String to, String code);

    /**
     * Sends the code that proves the address a signed-in user wants on the account.
     *
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendEmailBindCodeEmail(String to, String code);

    /**
     * Tells a replaced address that it no longer signs in, without naming the new one.
     *
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendEmailChangedNotice(String to);

    /**
     * Tells an account about an owner link (property-create intake A-12): fixed text with the kind, the time and what to
     * do if it was not them. No code, lead or address.
     *
     * @param kind one of the owner notice kinds
     * @throws vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException if sending fails
     */
    void sendOwnerNotice(String to, String kind, Instant occurredAt);
}
