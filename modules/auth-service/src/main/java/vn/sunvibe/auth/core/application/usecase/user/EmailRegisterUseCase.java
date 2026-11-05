package vn.sunvibe.auth.core.application.usecase.user;

import vn.sunvibe.auth.core.application.command.user.EmailRegisterCommand;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.exception.user.IdentifierConflictException;

/**
 * Registration by a proven mailbox: creates the account and starts its first session.
 */
public interface EmailRegisterUseCase {

    /**
     * @throws VerificationException       the token is unknown, expired, used or for another address, or the address was
     *                                     taken meanwhile; the token is not used up by that last refusal
     * @throws IdentifierConflictException the race for the address was lost at the unique index; nothing was written
     */
    AuthResult execute(EmailRegisterCommand command);
}
