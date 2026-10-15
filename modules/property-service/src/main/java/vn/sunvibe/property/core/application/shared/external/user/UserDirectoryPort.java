package vn.sunvibe.property.core.application.shared.external.user;

/**
 * Read-only view of user accounts, owned by another module. The adapter lives in the app module, so
 * property-service does not depend on auth-service.
 */
public interface UserDirectoryPort {

    /** True if a user with this id exists and may sign in (not deleted, locked or terminated). */
    boolean existsActiveUser(String userId);
}
