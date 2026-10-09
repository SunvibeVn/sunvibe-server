package vn.sunvibe.auth.core.shared.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AuthMessage {

    public static final String LOGIN_SUCCESS = "User logged in successfully.";
    public static final String CHANGE_PASSWORD_SUCCESS = "Change password successfully.";
    public static final String TOKEN_EXPIRED = "Token expired.";
    public static final String INVALID_TOKEN = "Invalid token.";
    public static final String BLACK_LIST_TOKEN = "Token is revoked.";
    public static final String PASSWORD_RESET_TOKEN_NOT_FOUND = "Password reset token not found.";

}
