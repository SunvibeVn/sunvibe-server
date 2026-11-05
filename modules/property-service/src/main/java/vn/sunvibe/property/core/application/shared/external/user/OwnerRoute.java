package vn.sunvibe.property.core.application.shared.external.user;

/**
 * Where an address an owner gave leads (property-create intake D5-5).
 *
 * @param userIdOrNull the account, only for {@link Outcome#FOUND}
 */
public record OwnerRoute(Outcome outcome, String userIdOrNull) {

    /** The answers of spec 4.6 step 1. */
    public enum Outcome {
        /** The address breaks the email policy. */
        INVALID,
        /** A confirmation can be bound to this account. */
        FOUND,
        /** Any other value. */
        NOT_FOUND
    }

    /** Whether an address matched is what a Sale must never learn: never print it. */
    @Override
    public String toString() {
        return "OwnerRoute[" + (outcome == Outcome.INVALID ? "invalid" : "checked") + "]";
    }
}
