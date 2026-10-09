package vn.sunvibe.auth.core.presentation.dto.response.user;

import lombok.Builder;

@Builder
public record UserProfileInfo(
//        @JsonSerialize(using = ToStringSerializer.class)
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
