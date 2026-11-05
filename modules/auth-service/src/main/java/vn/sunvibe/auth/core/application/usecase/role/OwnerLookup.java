package vn.sunvibe.auth.core.application.usecase.role;

/**
 * Who an address given by an owner routes to (property-create intake D5-5). {@code FOUND} is an account a confirmation can
 * be bound to, nothing more: routing is not proof (BR-5). The caller must answer found and not found alike.
 *
 * @param userIdOrNull the account, only for {@link Outcome#FOUND}
 */
public record OwnerLookup(Outcome outcome, String userIdOrNull) {

    /** The three answers of spec 4.6 step 1. */
    public enum Outcome {
        /** The address breaks the email policy; nothing was read. */
        INVALID,
        /** A verified, sign-in-able, non-staff account holds it. */
        FOUND,
        /** Every other value, on the same path: unknown, staff, unverified or phone-only. */
        NOT_FOUND
    }

    public static OwnerLookup invalid() {
        return new OwnerLookup(Outcome.INVALID, null);
    }

    public static OwnerLookup found(String userId) {
        return new OwnerLookup(Outcome.FOUND, userId);
    }

    public static OwnerLookup notFound() {
        return new OwnerLookup(Outcome.NOT_FOUND, null);
    }

    /** Whether an address matched is what a Sale must never learn: never print the account. */
    @Override
    public String toString() {
        return "OwnerLookup[" + (outcome == Outcome.INVALID ? "invalid" : "checked") + "]";
    }
}
