package vn.sunvibe.auth.core.application.usecase.user.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.impl.AuthResultAssembler;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.common.time.AppClock;

@Slf4j
@RequiredArgsConstructor
@Service
public class LoginService implements LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final AccessTokenGenerateUseCase accessTokenGenerateUseCase;
    private final RefreshTokenGenerateUseCase refreshTokenGenerateUseCase;
    private final UserDomainRepository userRepository;
    private final AuthResultAssembler authResultAssembler;

    @Transactional
    @Override
    public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
        try {
            // Authenticate by Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginCommand.username(), loginCommand.password())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            AppUser appUser = userRepository.findByUsername(userDetails.getUsername());

            return loginInternal(appUser, clientInfo);
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
    public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
        // Create access token
        String accessToken = accessTokenGenerateUseCase.generateAccessToken(appUser.getUsername().getValue());
        // Start a new sign-in session (refresh token family) for this device
        String refreshToken = refreshTokenGenerateUseCase.generateRefreshToken(appUser.getId(), clientInfo);

        // Update last login time of user
        appUser.setLastLogin(AppClock.currentUTC());
        userRepository.save(appUser);

        return authResultAssembler.assemble(appUser, accessToken, refreshToken, AuthMessage.LOGIN_SUCCESS);
    }

}
