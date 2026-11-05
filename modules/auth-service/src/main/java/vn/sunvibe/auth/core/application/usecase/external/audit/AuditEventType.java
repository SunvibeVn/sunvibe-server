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
    VERIFICATION_FAILED,
    /** An account never verified its phone within the allowed time and was deactivated. */
    PENDING_ACCOUNT_EXPIRED,
    /** A code was sent by SMS to a phone number that is registering (no account yet). */
    REGISTRATION_PHONE_CODE_SENT,
    /** A registering phone number was proven; a registration token was issued. */
    REGISTRATION_PHONE_VERIFIED,
    /** A registration code or token was rejected (wrong, expired, burned, used or for another phone). */
    REGISTRATION_VERIFICATION_FAILED,
    /** A code was sent to a mailbox that is registering (no account read yet). */
    REGISTRATION_EMAIL_CODE_SENT,
    /** A registering mailbox was proven. */
    REGISTRATION_EMAIL_VERIFIED,
    /** A bind code was sent to the address a signed-in user wants on the account. */
    EMAIL_BIND_CODE_SENT,
    /** A first verified email was bound to the account. */
    EMAIL_BOUND,
    /** The verified email of the account was replaced. */
    EMAIL_REPLACED,
    /** A bind was refused (wrong password, wrong or expired code, address not available). */
    EMAIL_BIND_FAILED,
    /** A phone operation was refused because the channel is closed for it. */
    AUTH_CHANNEL_REFUSED,
    /** The notice to a replaced address could not be delivered and was given up. */
    EMAIL_NOTICE_FAILED,
    /** A role was granted to an account (role admin API, or OWNER on property create); details name role and actor. */
    ROLE_GRANTED,
    /** A role was revoked from an account; every session of that account was ended. Details name role and actor. */
    ROLE_REVOKED

}
