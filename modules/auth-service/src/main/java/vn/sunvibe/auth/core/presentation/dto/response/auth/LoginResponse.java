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
 */
@Builder
public record LoginResponse(String tokenType, String accessToken, long expiresIn, String username, String userEmail,
                            Set<String> roles, String message) {
}
