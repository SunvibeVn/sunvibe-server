package vn.sunvibe.auth.core.domain.model.auth.enums;

/**
 * Where a one-time code is delivered: the account's phone number (SMS) or e-mail.
 */
public enum VerificationChannel {

    /** SMS to the account's phone number (its username). */
    PHONE,
    /** E-mail to the account's e-mail address. */
    EMAIL

}
