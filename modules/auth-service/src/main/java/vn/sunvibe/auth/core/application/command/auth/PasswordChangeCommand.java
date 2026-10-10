package vn.sunvibe.auth.core.application.command.auth;

import lombok.Builder;

/**
 * The signed-in user changes their password.
 *
 * @param currentPassword proves the user knows the password being replaced
 * @param newPassword     the new raw password, already validated for strength
 * @param clientInfo      the device that gets a fresh session; {@link ClientInfo#unknown()} when null
 */
@Builder
public record PasswordChangeCommand(
        String currentPassword,
        String newPassword,
        ClientInfo clientInfo
) {
}
