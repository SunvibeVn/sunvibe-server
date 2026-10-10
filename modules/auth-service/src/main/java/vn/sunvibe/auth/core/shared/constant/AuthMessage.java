package vn.sunvibe.auth.core.shared.constant;

/**
 * Client-facing messages of the auth endpoints.
 */
public final class AuthMessage {

    public static final String LOGIN_SUCCESS = "User logged in successfully.";
    public static final String CHANGE_PASSWORD_SUCCESS = "Change password successfully.";
    public static final String PASSWORD_RESET_SUCCESS = "Password reset successfully";
    public static final String TOKEN_REFRESH_SUCCESS = "Token refreshed successfully.";
    /** Single client-facing message for every refresh failure; the precise reason is only logged. */
    public static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";
    public static final String LOGOUT_SUCCESS = "Logged out successfully.";
    public static final String LOGOUT_ALL_SUCCESS = "Logged out from all devices successfully.";

    private AuthMessage() {
    }

}
