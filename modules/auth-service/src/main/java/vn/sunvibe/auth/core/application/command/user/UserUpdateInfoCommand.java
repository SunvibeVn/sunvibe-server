package vn.sunvibe.auth.core.application.command.user;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserUpdateInfoCommand(
        String fullName,
        String username,
        String email,
        String password,
        Set<String> roles
) {
}
