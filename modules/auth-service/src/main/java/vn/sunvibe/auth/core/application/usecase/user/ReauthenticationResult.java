package vn.sunvibe.auth.core.application.usecase.user;

import java.time.Duration;

/**
 * What a reauthentication found.
 *
 * @param credentialVersion the version the checked password belonged to, to compare under a later row lock
 * @param lockedFor         how long the counter refuses, for {@link Outcome#LOCKED}; otherwise null
 */
public record ReauthenticationResult(Outcome outcome, long credentialVersion, Duration lockedFor) {

    /** The answer of a password check. */
    public enum Outcome {
        OK,
        WRONG,
        LOCKED
    }

    public static ReauthenticationResult ok(long credentialVersion) {
        return new ReauthenticationResult(Outcome.OK, credentialVersion, null);
    }

    public static ReauthenticationResult wrong() {
        return new ReauthenticationResult(Outcome.WRONG, 0, null);
    }

    public static ReauthenticationResult locked(Duration lockedFor) {
        return new ReauthenticationResult(Outcome.LOCKED, 0, lockedFor);
    }
}
