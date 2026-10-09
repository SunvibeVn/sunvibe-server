package vn.sunvibe.auth.core.application.usecase.user.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.time.Clock;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.presentation.dto.response.auth.LoginResponse;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.auth.core.shared.service.CookieService;
import vn.sunvibe.common.time.AppClock;

@Slf4j
@RequiredArgsConstructor
@Service
public class LoginService implements LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final AccessTokenGenerateUseCase accessTokenGenerateUseCase;
    private final RefreshTokenGenerateUseCase refreshTokenGenerateUseCase;
    private final UserDomainRepository userRepository;
    private final CookieService cookieService;

    @Transactional
    @Override
    public AuthResult execute(LoginCommand loginCommand, String rawFingerprint) {
        try {
            // Authenticate by Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginCommand.username(), loginCommand.password())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            AppUser appUser = userRepository.findByUsername(userDetails.getUsername());

            return loginInternal(appUser, rawFingerprint);
        } catch (BadCredentialsException e) {
            log.error("Authenticate failed.");
            throw new AuthenticationFailedException("Incorrect username or password.");
        } catch (AccountStatusException e) {
            log.error("Authenticate failed: account is locked or disabled.");
            throw new AuthenticationFailedException("Account is locked or disabled.");
        }
    }

    @Transactional
    @Override
    public AuthResult loginInternal(AppUser appUser, String rawFingerprint) {
        // Create access token
        String accessToken = accessTokenGenerateUseCase.generateAccessToken(appUser.getUsername().getValue());
        // Create refresh token
        String refreshToken = refreshTokenGenerateUseCase.generateRefreshToken(appUser.getId().getValue(), rawFingerprint);

        // Build cookies to save access token & refresh token
        ResponseCookie accessCookie = cookieService.buildAccessTokenCookie(accessToken);
        ResponseCookie refreshCookie = cookieService.buildRefreshTokenCookie(refreshToken);

        // Update last login time of user
        appUser.setLastLogin(AppClock.currentUTC());
        userRepository.save(appUser);

        LoginResponse loginResponse = LoginResponse.builder()
                .tokenType(JWTProvider.TOKEN_TYPE)
                .username(appUser.getUsername().getValue())
                .userEmail(appUser.getEmail() != null ? appUser.getEmail().getValue() : null)
                .roles(appUser.getUserRoles())
                .message(AuthMessage.LOGIN_SUCCESS)
                .build();

        return AuthResult.builder()
                .accessCookie(accessCookie)
                .refreshCookie(refreshCookie)
                .loginResponse(loginResponse)
                .build();
    }

}
