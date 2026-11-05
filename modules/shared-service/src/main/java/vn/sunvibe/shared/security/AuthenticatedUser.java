package vn.sunvibe.shared.security;

/**
 * Implemented by the principal of an authenticated request, so any module can read the caller's user id
 * without depending on auth-service. {@code Authentication.getName()} is the same user id.
 */
public interface AuthenticatedUser {

    /** The caller's user id (ULID), taken from the reloaded account, never from the token alone. */
    String getUserId();
}
