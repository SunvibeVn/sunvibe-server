package vn.sunvibe.auth.core.domain.model.user.enums;

/**
 * Lifecycle of an account. Only {@link #LOCK}, {@link #TERMINATED} and
 * {@link #DELETED} stop the account from signing in.
 */
public enum UserStatus {

    /** Phone number verified; fully usable. */
    ACTIVE,
    /** Locked by an administrator. */
    LOCK,
    /** Closed for good. */
    TERMINATED,
    /** Registered, phone number not verified yet; deactivated if it stays so too long. */
    PENDING,
    /** Soft-deleted; its phone number and e-mail can be registered again. */
    DELETED

}
