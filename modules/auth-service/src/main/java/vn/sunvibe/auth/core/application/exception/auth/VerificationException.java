package vn.sunvibe.auth.core.application.exception.auth;

import lombok.Getter;
import vn.sunvibe.shared.exception.ApplicationException;

import java.io.Serial;

/**
 * A verification code cannot be sent or confirmed (wrong/expired code, nothing
 * to verify, already verified). Answered with 400. The {@link Reason} lets a
 * handler pick the error code without reading the message.
 */
@Getter
public class VerificationException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public VerificationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** Why the code was refused. */
    public enum Reason {
        /** The code is wrong and attempts are left. */
        CODE_INCORRECT,
        /** No usable code: none sent, expired, replaced or sent to another address. */
        CODE_EXPIRED,
        /** The code was burned after too many wrong tries. */
        CODE_ATTEMPTS_EXCEEDED,
        /** The contact is already verified. */
        ALREADY_VERIFIED,
        /** E-mail verification on an account without e-mail. */
        NO_EMAIL,
        /** The registration token is missing, used, expired or bound to another phone number. */
        REGISTRATION_TOKEN_INVALID
    }
}
