package vn.sunvibe.auth.core.presentation.dto.response.user;

import lombok.Builder;

/**
 * Body of {@code GET /api/user/query/my-info}.
 */
@Builder
public record UserProfileInfo(
        String fullName,
        String username,
        String email,
        String profileImageUrl,
        String kycStatus,
        String accountStatus,
        boolean phoneVerified,
        boolean emailVerified
) {
}
