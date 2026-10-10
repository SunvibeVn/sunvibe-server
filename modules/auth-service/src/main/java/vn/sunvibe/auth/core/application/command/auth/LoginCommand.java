package vn.sunvibe.auth.core.application.command.auth;

import lombok.Builder;

/**
 * Password login.
 *
 * @param username the phone number the account was registered with
 * @param password the raw password
 */
@Builder
public record LoginCommand(
        String username,
        String password
) {
}
