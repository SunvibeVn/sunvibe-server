package vn.sunvibe.auth.core.shared.constant;

/**
 * Authorities granted besides roles. Other modules can require them, e.g.
 * {@code @PreAuthorize("hasAuthority('PHONE_VERIFIED')")} on actions that need
 * a confirmed phone number (booking, posting a listing, ...).
 */
public final class AuthAuthorities {

    /** The user proved control of the phone number (account no longer PENDING). */
    public static final String PHONE_VERIFIED = "PHONE_VERIFIED";
    /** The user proved control of the e-mail on the account. */
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";

    private AuthAuthorities() {
    }
}
