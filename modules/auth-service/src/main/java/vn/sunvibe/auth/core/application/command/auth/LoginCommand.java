package vn.sunvibe.auth.core.application.command.auth;

import lombok.Builder;

@Builder
public record LoginCommand(
        String username,
        String password
) {
}
