package vn.sunvibe.auth.core.application.usecase.role;

/**
 * The account named as a property owner (auth-roles BR-7, BR-8). Published for other modules: the app module
 * adapts it to property-service's user directory port, so the modules do not depend on each other.
 */
public interface OwnerAccountUseCase {

    /** True if the account exists, may sign in (not deleted, locked or terminated) and holds no staff/admin role. */
    boolean isEligibleOwner(String userId);

    /**
     * Grants OWNER to the account if it does not hold it yet, in the caller's transaction, audited with
     * {@code actorUserId} as the actor once that transaction commits. Callable only from an operation that is
     * already authorized for staff (property create).
     *
     * @return false, granting nothing, if the account is no longer an eligible owner (checked again here)
     */
    boolean ensureOwnerRole(String ownerUserId, String actorUserId);

    /**
     * Routes an address an owner gave to an account a confirmation can be bound to (property-create intake D5-5). The email
     * policy runs first, then one query. Callers answer FOUND and NOT_FOUND alike; a match proves nothing (BR-5).
     */
    OwnerLookup lookupConfirmable(String email);

    /** A masked address and the account's age, for the manager's check (property-create intake spec 4.6 step 6). */
    OwnerAccountContext accountContext(String userId);

    /**
     * Whether the account may own now and how often it has lost sign-in (property-create intake D5-10): read from the row,
     * never from a managed entity, with a soft-deleted account as not eligible.
     */
    OwnerStanding ownerStanding(String userId);

    /**
     * Writes a fixed notice to the account's verified address, in the caller's transaction, so it commits with the command
     * or not at all (property-create intake A-12). It holds no code, lead or address.
     */
    void notifyOwner(String userId, OwnerNoticeKind kind);

}
