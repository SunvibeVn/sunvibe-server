package vn.sunvibe.auth.core.application.query.user;

import lombok.Builder;

/**
 * The signed-in user's own profile.
 *
 * @param email         null when the account has no e-mail
 * @param kycStatus     name of the {@code KycStatus}
 * @param accountStatus name of the {@code UserStatus}
 */
@Builder
public record UserProfileResult(String fullName, String username, String email, String profileImageUrl,
                                String kycStatus, String accountStatus, boolean phoneVerified,
                                boolean emailVerified) {
}
