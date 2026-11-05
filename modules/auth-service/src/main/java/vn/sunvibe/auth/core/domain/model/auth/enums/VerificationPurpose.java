package vn.sunvibe.auth.core.domain.model.auth.enums;

/**
 * What a verification code proves. A code of one purpose never answers another, because {@code verification_codes} is
 * also read by channel (auth-email-access spec 4.7).
 */
public enum VerificationPurpose {
    /** Control of the contact the account already has. */
    CONTACT,
    /** Control of the address a signed-in user wants on the account. */
    EMAIL_BIND
}
