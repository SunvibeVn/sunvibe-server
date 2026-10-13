package vn.sunvibe.auth.core.application.usecase.auth;

import vn.sunvibe.auth.core.application.exception.auth.VerificationException;
import vn.sunvibe.auth.core.application.exception.auth.VerificationThrottledException;
import vn.sunvibe.auth.core.application.query.auth.RegistrationTokenResult;
import vn.sunvibe.auth.core.application.query.auth.VerificationSendResult;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

/**
 * "Verify first, register later": proves that someone controls a phone number
 * before an account exists for it.
 */
public interface RegistrationPhoneVerificationUseCase {

    /**
     * Sends a new code to the phone number; older codes stop working.
     *
     * @throws AuthValidationException        the phone number already has an account
     * @throws VerificationThrottledException asked too soon after the previous code, or too often today
     */
    VerificationSendResult sendCode(String phoneNumber);

    /**
     * Checks the code and hands out the single-use token registration needs.
     *
     * @throws VerificationException wrong, expired, burned or missing code
     */
    RegistrationTokenResult verifyCode(String phoneNumber, String code);

    /**
     * Uses the token up for the phone number it was issued to. Called by
     * registration inside its own transaction, so a failed registration gives
     * the token back.
     *
     * @throws VerificationException the token is unknown, expired, used or issued to another phone number
     */
    void consumeRegistrationToken(String registrationToken, String phoneNumber);

}
