package vn.sunvibe.auth.core.application.usecase.external.audit;

/**
 * Security-relevant events of the auth module, kept in the audit log.
 */
public enum AuditEventType {

    /** Password login succeeded and a session was started. */
    LOGIN_SUCCEEDED,
    /** Wrong credentials, or an account that may not sign in. */
    LOGIN_FAILED,
    /** Login refused up front because the account is temporarily locked. */
    LOGIN_BLOCKED,
    /** Too many wrong passwords: password login locked for a while. */
    ACCOUNT_TEMPORARILY_LOCKED,
    /** A new account was registered. */
    USER_REGISTERED,
    /** One session ended by the user. */
    LOGOUT,
    /** Every session of the user ended by the user. */
    LOGOUT_ALL_DEVICES,
    /** A rotated refresh token was replayed: the session was revoked (likely theft). */
    REFRESH_TOKEN_REUSE_DETECTED,
    /** A refresh for a missing, locked or disabled account: the session was revoked. */
    REFRESH_REJECTED_ACCOUNT_INACTIVE,
    /** Password changed by the signed-in user; other sessions revoked. */
    PASSWORD_CHANGED,
    /** Password change refused (wrong current password). */
    PASSWORD_CHANGE_FAILED,
    /** A password-reset code was asked for (whether or not one was sent). */
    PASSWORD_RESET_REQUESTED,
    /** Password reset through the e-mailed OTP; every session revoked. */
    PASSWORD_RESET_COMPLETED,
    /** Password reset refused (wrong OTP, unknown/expired/burned token). */
    PASSWORD_RESET_FAILED,
    /** A verification code was sent by SMS to the account's phone number. */
    PHONE_VERIFICATION_SENT,
    /** A verification code was sent to the account's e-mail. */
    EMAIL_VERIFICATION_SENT,
    /** The user proved control of the phone number (by code, or by an SMS password reset). */
    PHONE_VERIFIED,
    /** The user proved control of the e-mail. */
    EMAIL_VERIFIED,
    /** A verification code was rejected (wrong, expired, burned). */
    VERIFICATION_FAILED

}
