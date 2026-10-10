package vn.sunvibe.auth.core.presentation.dto.response.auth;

import lombok.Builder;

import java.util.Set;

/**
 * Body returned by login, register, password change and token refresh.
 * The refresh token is never in the body: it travels only in its HttpOnly cookie.
 *
 * @param accessToken the JWT to send as {@code Authorization: Bearer ...}
 *                    (it is also set as an HttpOnly cookie)
 * @param expiresIn   seconds until {@code accessToken} expires; refresh shortly before
 * @param phoneVerified false right after registration: show the "enter the SMS code" screen
 * @param emailVerified false when there is no e-mail or it is not confirmed yet
 */
@Builder
public record LoginResponse(String tokenType, String accessToken, long expiresIn, String username, String userEmail,
                            Set<String> roles, boolean phoneVerified, boolean emailVerified, String message) {
}
