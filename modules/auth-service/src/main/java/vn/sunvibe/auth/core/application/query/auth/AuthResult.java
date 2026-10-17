package vn.sunvibe.auth.core.application.query.auth;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

import java.util.Set;

/**
 * A sign-in session that was just started or refreshed: the token pair plus a
 * summary of the account for the client.
 *
 * @param accessToken   signed JWT for {@code Authorization: Bearer ...}
 * @param refreshToken  secret for the refresh-token cookie; only its hash is stored
 * @param email         null when the account has no e-mail
 * @param phoneVerified false right after registration
 * @param emailVerified false when there is no e-mail or it is not confirmed yet
 * @param userId        opaque ULID of the account
 * @param fullName      display name of the account
 */
public record AuthResult(String accessToken, String refreshToken, String username, String email, Set<String> roles,
                         boolean phoneVerified, boolean emailVerified, String userId, String fullName) {

    public static AuthResult of(AppUser user, String accessToken, String refreshToken) {
        return new AuthResult(
                accessToken,
                refreshToken,
                user.getUsername().getValue(),
                user.getEmail() != null ? user.getEmail().getValue() : null,
                user.getUserRoles() != null ? Set.copyOf(user.getUserRoles()) : Set.of(),
                user.isPhoneVerified(),
                user.isEmailVerified(),
                user.getId().getValue(),
                user.getFullName());
    }

    /** Both tokens stay out of logs. */
    @Override
    public String toString() {
        return "AuthResult[accessToken=[hidden], refreshToken=[hidden], username=" + username + ", email=" + email
                + ", roles=" + roles + ", phoneVerified=" + phoneVerified + ", emailVerified=" + emailVerified
                + ", userId=" + userId + ", fullName=" + fullName + "]";
    }
}
