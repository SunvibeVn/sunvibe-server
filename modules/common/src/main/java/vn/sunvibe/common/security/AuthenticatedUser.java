package vn.sunvibe.common.security;

/**
 * Implemented by the principal of an authenticated request, so any module can read the caller's user id
 * without depending on auth-service. {@code Authentication.getName()} stays the username.
 */
public interface AuthenticatedUser {

    /** The caller's user id (ULID), taken from the reloaded account, never from the token alone. */
    String getUserId();
}
