package vn.sunvibe.auth.core.application.shared;

/** What an account has to do before it can use the app fully (auth-email-access 4.6); none is a null. */
public enum RequiredAction {
    /** Bind a proven email: the phone channel cannot carry this account. */
    BIND_EMAIL,
    /** Confirm the phone number by SMS. */
    VERIFY_PHONE
}
