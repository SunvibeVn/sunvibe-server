package vn.sunvibe.auth.core.application.command.user;

import lombok.Builder;

/**
 * Self-registration input. {@code fullName}, {@code username} and {@code password}
 * are required; {@code email} and {@code idCardNumber} are optional (null or blank
 * means "not provided") and can be added to the profile later.
 */
@Builder
public record UserRegisterCommand(
        String fullName,
        String username,
        String email,
        String password,
        String idCardNumber
) {
}
