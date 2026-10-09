package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.LoginCommand;
import vn.sunvibe.auth.core.application.exception.auth.AccountTemporarilyLockedException;
import vn.sunvibe.auth.core.application.exception.auth.AuthenticationFailedException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.LoginLockoutPolicy;
import vn.sunvibe.auth.core.application.usecase.auth.AccessTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.impl.AuthResultAssembler;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.common.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Password login with progressive lockout ({@link LoginLockoutPolicy}).
 * <p>
 * A temporarily locked account is refused before the password is checked, so
 * guesses made during the lock reveal nothing. Failed attempts are counted with
 * an atomic update and committed even though the request fails
 * ({@code noRollbackFor}).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LoginService implements LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final AccessTokenGenerateUseCase accessTokenGenerateUseCase;
    private final RefreshTokenGenerateUseCase refreshTokenGenerateUseCase;
    private final UserDomainRepository userRepository;
    private final AuthResultAssembler authResultAssembler;
    private final UserLoginAttemptRepository loginAttemptRepository;
    private final LoginLockoutPolicy lockoutPolicy;

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    @Override
    public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
        Instant now = AppClock.currentUTC();
        Optional<AppUser> account = findAccount(loginCommand.username());

        if (account.isPresent() && account.get().isLoginTemporarilyLocked(now)) {
            log.warn("Login refused for user {}: temporarily locked", account.get().getId().getValue());
            throw new AccountTemporarilyLockedException(Duration.between(now, account.get().getLockedUntil()));
        }

        try {
            // Authenticate by Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginCommand.username(), loginCommand.password())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (BadCredentialsException e) {
            // Unknown usernames leave nothing to count; the per-IP rate limit covers them
            account.ifPresent(user -> recordFailedAttempt(user, now));
            log.warn("Authenticate failed: bad credentials");
            throw new AuthenticationFailedException("Incorrect username or password.");
        } catch (AccountStatusException e) {
            log.warn("Authenticate failed: account is locked or disabled.");
            throw new AuthenticationFailedException("Account is locked or disabled.");
        }

        AppUser appUser = account.orElseGet(() -> userRepository.findByUsername(loginCommand.username()));
        return loginInternal(appUser, clientInfo);
    }

    @Transactional
    @Override
    public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
        // Create access token
        String accessToken = accessTokenGenerateUseCase.generateAccessToken(appUser.getUsername().getValue());
        // Start a new sign-in session (refresh token family) for this device
        String refreshToken = refreshTokenGenerateUseCase.generateRefreshToken(appUser.getId(), clientInfo);

        // Record the login and forget earlier failed attempts
        appUser.recordLoginSuccess(AppClock.currentUTC());
        userRepository.save(appUser);

        return authResultAssembler.assemble(appUser, accessToken, refreshToken, AuthMessage.LOGIN_SUCCESS);
    }

    private void recordFailedAttempt(AppUser user, Instant now) {
        int failedAttempts = loginAttemptRepository.incrementFailedLoginAttempts(user.getId());
        Optional<Duration> lock = lockoutPolicy.lockDurationAfter(failedAttempts);
        if (lock.isPresent()) {
            loginAttemptRepository.lockLoginUntil(user.getId(), now.plus(lock.get()));
            log.warn("User {} locked out for {} after {} failed login attempts",
                    user.getId().getValue(), lock.get(), failedAttempts);
            throw new AccountTemporarilyLockedException(lock.get());
        }
    }

    private Optional<AppUser> findAccount(String username) {
        try {
            return Optional.of(userRepository.findByUsername(username));
        } catch (UserNotFoundException e) {
            return Optional.empty();
        }
    }

}
