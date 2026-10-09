package vn.sunvibe.auth.core.shared.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AuthMessage {

    public static final String LOGIN_SUCCESS = "User logged in successfully.";
    public static final String CHANGE_PASSWORD_SUCCESS = "Change password successfully.";
    public static final String TOKEN_REFRESH_SUCCESS = "Token refreshed successfully.";
    /** Single client-facing message for every refresh failure; the precise reason is only logged. */
    public static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";
    public static final String LOGOUT_SUCCESS = "Logged out successfully.";
    public static final String LOGOUT_ALL_SUCCESS = "Logged out from all devices successfully.";
    public static final String TOKEN_EXPIRED = "Token expired.";
    public static final String INVALID_TOKEN = "Invalid token.";
    public static final String BLACK_LIST_TOKEN = "Token is revoked.";
    public static final String PASSWORD_RESET_TOKEN_NOT_FOUND = "Password reset token not found.";

}
