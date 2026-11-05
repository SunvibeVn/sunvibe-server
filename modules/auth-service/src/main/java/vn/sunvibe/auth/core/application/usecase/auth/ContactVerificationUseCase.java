package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.exception.auth.AuthChannelDisabledException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

/**
 * Proves that the signed-in user controls the phone number of the account. An email is proven by binding it
 * (auth-email-access D4-4).
 */
public interface ContactVerificationUseCase {

    /**
     * Sends a new code to the phone; older codes stop working.
     *
     * @throws AuthChannelDisabledException   the phone channel is closed for this
     * @throws VerificationException          nothing to verify (no phone, already verified)
     * @throws VerificationThrottledException asked too soon after the previous code, or too often today
     */
    VerificationSendResult sendCode();

    /**
     * Checks the code and marks the phone verified; a PENDING account becomes ACTIVE.
     *
     * @throws AuthChannelDisabledException the phone channel is closed for this
     * @throws VerificationException        wrong, expired, burned or missing code
     */
    void confirm(String code);

    /**
     * Sends a first code right after registration. Never throws: if delivery
     * fails the user can ask again with {@link #sendCode}.
     */
    void sendCodeAfterRegistration(AppUser user);

}
