package vn.sunvibe.property.core.application.shared.external.user;

import java.time.Duration;

/**
 * What a password check found (property-create intake spec 4.6 step 4, auth 4.9).
 *
 * @param lockedForOrNull how long the counter refuses, for {@link Outcome#LOCKED}
 */
public record PasswordCheck(Outcome outcome, Duration lockedForOrNull) {

    /** The answer of the check. */
    public enum Outcome {
        OK,
        WRONG,
        LOCKED
    }
}
