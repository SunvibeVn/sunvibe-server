package vn.sunvibe.auth.core.application.command.user;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserRegisterCommand(
        String fullName,
        String username,
        String email,
        String password,
        String idCardNumber,
        Set<String> roles
) {
}
