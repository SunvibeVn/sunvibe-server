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

    /**
     * The account an address an owner gave routes to (property-create intake D5-5). Routing is not proof (property-create BR-5): the caller
     * answers FOUND and NOT_FOUND alike.
     */
    OwnerRoute lookupConfirmable(String email);

    /** A masked address and the account's age, for the manager's check (property-create intake spec 4.6 step 6). */
    OwnerAccountFacts accountContext(String userId);

    /** Whether the account may own now and its count of lost sign-ins, read from the row (intake D5-10). */
    OwnerStandingFacts ownerStanding(String userId);

    /** Writes a fixed notice in the caller's transaction, so it commits with the command (intake A-12). */
    void notifyOwner(String userId, OwnerNotice notice);

    /**
     * Asks a signed-in owner for the password again (property-create intake spec 4.6 step 4). It commits its own counter, so it runs before
     * any transaction of the caller and holds no row lock through bcrypt.
     */
    PasswordCheck checkPassword(String userId, String password);
}
