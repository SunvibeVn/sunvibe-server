package vn.sunvibe.auth.core.domain.model.auth.enums;

/**
 * Why a sign-in session's refresh tokens were revoked; kept for auditing.
 */
public enum RevokeReason {

    /** The user signed out on this device. */
    LOGOUT,
    /** The user signed out on every device. */
    LOGOUT_ALL,
    /** The user changed their password; other sessions are ended. */
    PASSWORD_CHANGED,
    /** The password was reset through the OTP flow; every session is ended. */
    PASSWORD_RESET,
    /** An already-rotated token was presented again: likely stolen. */
    REUSE_DETECTED,
    /** The owner no longer exists or is locked/disabled. */
    ACCOUNT_INACTIVE,
    /** A role was revoked from the account by staff; every session is ended (auth-roles BR-11). */
    ROLE_REVOKED

}
