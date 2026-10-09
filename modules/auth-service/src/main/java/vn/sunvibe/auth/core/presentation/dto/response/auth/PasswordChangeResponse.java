package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;

@Builder
public record PasswordChangeResponse(
        String username
) {
}
