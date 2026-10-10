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
 */
public record AuthResult(String accessToken, String refreshToken, String username, String email, Set<String> roles,
                         boolean phoneVerified, boolean emailVerified) {

    public static AuthResult of(AppUser user, String accessToken, String refreshToken) {
        return new AuthResult(
                accessToken,
                refreshToken,
                user.getUsername().getValue(),
                user.getEmail() != null ? user.getEmail().getValue() : null,
                user.getUserRoles() != null ? Set.copyOf(user.getUserRoles()) : Set.of(),
                user.isPhoneVerified(),
                user.isEmailVerified());
    }
}
