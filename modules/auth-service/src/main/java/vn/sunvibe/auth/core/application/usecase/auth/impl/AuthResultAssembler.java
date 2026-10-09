package vn.sunvibe.auth.core.application.usecase.auth.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.shared.service.CookieService;

/**
 * Builds the response of every flow that hands out a token pair (login and
 * refresh), so both return the same cookies and body.
 */
@Component
@RequiredArgsConstructor
public class AuthResultAssembler {

    private final CookieService cookieService;

    public AuthResult assemble(AppUser appUser, String accessToken, String refreshToken, String message) {
        LoginResponse loginResponse = LoginResponse.builder()
                .tokenType(JWTProvider.TOKEN_TYPE)
                .accessToken(accessToken)
                .expiresIn(cookieService.accessTokenTtl().toSeconds())
                .username(appUser.getUsername().getValue())
                .userEmail(appUser.getEmail() != null ? appUser.getEmail().getValue() : null)
                .roles(appUser.getUserRoles())
                .message(message)
                .build();

        return AuthResult.builder()
                .accessCookie(cookieService.buildAccessTokenCookie(accessToken))
                .refreshCookie(cookieService.buildRefreshTokenCookie(refreshToken))
                .loginResponse(loginResponse)
                .build();
    }
}
