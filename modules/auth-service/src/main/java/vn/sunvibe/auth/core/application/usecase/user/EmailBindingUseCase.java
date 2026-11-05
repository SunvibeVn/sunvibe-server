package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.user.EmailBindConfirmCommand;
import vn.sunvibe.auth.core.application.command.user.EmailBindSendCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.exception.user.IdentifierConflictException;

/**
 * Binds or replaces the email of the signed-in account (auth-email-access spec 4.9): the only way an address becomes
 * verified on an account that exists.
 */
public interface EmailBindingUseCase {

    /**
     * Asks for the password again, then mails a code to the new address. Whether another account holds the address does
     * not show here.
     *
     * @throws AuthValidationException           wrong password, on {@code currentPassword}, or a refused address
     * @throws AccountTemporarilyLockedException too many wrong passwords on the reauthentication counter
     * @throws VerificationException             the address already is the verified one of this account
     * @throws VerificationThrottledException    asked too soon or too often
     * @throws SendingMailFailedException        the mail could not leave; nothing was kept
     */
    VerificationSendResult sendCode(EmailBindSendCommand command);

    /**
     * Checks the code and writes the address: every other session ends, this device gets a new one, and older proofs and
     * access tokens die.
     *
     * @throws VerificationException       wrong, expired or burned code, a code for another address or an older version,
     *                                     or an address another account holds, which uses the code up
     * @throws IdentifierConflictException a race lost at the unique index; nothing was written
     */
    AuthResult confirm(EmailBindConfirmCommand command);
}
