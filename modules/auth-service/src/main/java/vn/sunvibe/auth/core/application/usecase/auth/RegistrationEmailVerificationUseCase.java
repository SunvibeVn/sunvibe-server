package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

/**
 * "Verify first, register later" by email: proves that someone controls a mailbox before an account exists for it.
 */
public interface RegistrationEmailVerificationUseCase {

    /**
     * Mails a new code; an older unused code stops working. Reads no account, so the answer is the same for every valid
     * address (auth-email-access D4-5).
     *
     * @throws AuthValidationException        the value breaks the email policy
     * @throws VerificationThrottledException asked too soon after the previous code, or too often today
     * @throws SendingMailFailedException     the mail could not leave; nothing was kept, so a retry is free
     */
    VerificationSendResult sendCode(String email);

    /**
     * Checks the code. Only now does the answer depend on who holds the address, and only the mailbox holder sees it.
     *
     * @return the single-use token registration needs, for an address no one holds
     * @throws VerificationException wrong, expired, burned or missing code, or an address that cannot register
     */
    RegistrationTokenResult verifyCode(String email, String code);

}
