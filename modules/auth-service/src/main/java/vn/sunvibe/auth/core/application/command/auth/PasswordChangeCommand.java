package vn.sunvibe.auth.core.application.command.auth;

import lombok.Builder;

@Builder
public record PasswordChangeCommand(
        String currentPassword,
        String newPassword
) {
}
