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

}
