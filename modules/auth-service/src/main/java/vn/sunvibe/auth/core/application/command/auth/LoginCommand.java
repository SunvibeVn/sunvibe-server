package vn.sunvibe.auth.core.application.command.auth;

import lombok.Builder;

/**
 * Password login, by exactly one identifier.
 *
 * @param email    the address as typed, or null
 * @param username the phone as typed, or null
 * @param password the raw password
 */
@Builder
public record LoginCommand(
        String email,
        String username,
        String password
) {

    public static LoginCommand byEmail(String email, String password) {
        return new LoginCommand(email, null, password);
    }

    public static LoginCommand byPhone(String username, String password) {
        return new LoginCommand(null, username, password);
    }

    /** The password and the identifiers stay out of logs. */
    @Override
    public String toString() {
        return "LoginCommand[hidden]";
    }
}
