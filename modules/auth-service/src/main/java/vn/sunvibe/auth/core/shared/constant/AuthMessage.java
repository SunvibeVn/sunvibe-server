package vn.sunvibe.auth.core.shared.constant;

/**
 * Client-facing messages of the auth endpoints.
 */
public final class AuthMessage {

    /** Single client-facing message for every refresh failure; the precise reason is only logged. */
    public static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";

    private AuthMessage() {
    }

}
