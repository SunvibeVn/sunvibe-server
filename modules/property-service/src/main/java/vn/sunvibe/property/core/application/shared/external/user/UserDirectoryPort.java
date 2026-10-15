package vn.sunvibe.property.core.application.shared.external.user;

/**
 * Read-only view of user accounts, owned by another module. The adapter lives in the app module, so
 * property-service does not depend on auth-service.
 */
public interface UserDirectoryPort {

    /**
     * True if the user may own a property: exists, may sign in (not deleted, locked or terminated) and holds no
     * staff/admin role (auth-roles BR-7).
     */
    boolean isEligibleOwner(String userId);

    /**
     * Grants the OWNER role to the user if missing, in the caller's transaction, audited with {@code actorUserId}
     * as the actor (auth-roles BR-8).
     *
     * @return false, granting nothing, if the user stopped being an eligible owner since {@link #isEligibleOwner}
     */
    boolean ensureOwnerRole(String ownerUserId, String actorUserId);
}
