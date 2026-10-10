package vn.sunvibe.auth.core.shared.constant;

/**
 * Client-facing messages about user accounts.
 */
public final class UserMessage {

    public static final String USER_CREATE_SUCCESS = "User registered successfully.";
    /** Format argument: the username. */
    public static final String USER_NOT_FOUND_WITH_USERNAME = "User with username %s not found.";
    /** Format argument: the e-mail. */
    public static final String USER_NOT_FOUND_WITH_EMAIL = "User with email %s not found.";

    private UserMessage() {
    }

}
