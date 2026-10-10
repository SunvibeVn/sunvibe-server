package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

/**
 * Proves that the signed-in user controls their phone number (SMS) or e-mail.
 */
public interface ContactVerificationUseCase {

    /**
     * Sends a new code to the signed-in user's phone or e-mail; older codes stop working.
     *
     * @throws VerificationException          nothing to verify (no e-mail, already verified)
     * @throws VerificationThrottledException asked too soon after the previous code, or too often today
     */
    VerificationSendResult sendCode(VerificationChannel channel);

    /**
     * Checks the code and marks the phone (PENDING account becomes ACTIVE) or e-mail verified.
     *
     * @throws VerificationException wrong, expired, burned or missing code
     */
    void confirm(VerificationChannel channel, String code);

    /**
     * Sends a first code right after registration. Never throws: if delivery
     * fails the user can ask again with {@link #sendCode}.
     */
    void sendCodeAfterRegistration(AppUser user, VerificationChannel channel);

}
