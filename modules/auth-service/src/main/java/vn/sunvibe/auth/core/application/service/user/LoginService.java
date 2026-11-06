package vn.sunvibe.auth.core.application.service.user;

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
import vn.sunvibe.auth.core.application.shared.AccountAccessPolicy;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.shared.VerifiedCredentials;
import vn.sunvibe.auth.core.application.usecase.auth.RefreshTokenGenerateUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.application.usecase.user.LoginIdentifier;
import vn.sunvibe.auth.core.application.usecase.user.LoginUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Password login by email or phone, with progressive lockout ({@link LoginLockoutPolicy}), in the check order of
 * auth-email-access spec 5.5.
 * <p>
 * An account the identifier does not reach counts as not found: no lock check and no counter. A temporarily locked
 * account is refused before the password is checked, so guesses made during the lock reveal nothing. The password is
 * always hashed, and the status of the account is decided after it, so a locked account answers no faster. Failed
 * attempts are counted with an atomic update and committed even though the request fails ({@code noRollbackFor}).
 * <p>
 * The password is checked without a row lock, because bcrypt is slow. So the row is locked and read again afterwards: a
 * password change, reset or bind that committed in between leaves no session (SV-001). The aggregate is never written
 * back, or it would restore the old hash; a login writes three columns only.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class LoginService implements LoginUseCase {

    private static final String REFUSED = "Incorrect username or password.";

    private final AuthenticationManager authenticationManager;
    private final AccessTokenIssuerPort accessTokenIssuer;
    private final RefreshTokenGenerateUseCase refreshTokenGenerateUseCase;
    private final UserDomainRepository userRepository;
    private final UserLoginAttemptRepository loginAttemptRepository;
    private final LoginLockoutPolicy lockoutPolicy;
    private final LoginAccountFinder loginAccounts;
    private final PhoneChannelPolicy phoneChannel;
    private final AuditLogPort auditLog;
    private final AccountAccessPolicy accountAccess;

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    @Override
    public AuthResult execute(LoginCommand loginCommand, ClientInfo clientInfo) {
        Instant now = AppClock.currentUTC();
        LoginIdentifier identifier = identifierOf(loginCommand);
        Optional<AppUser> account = loginAccounts.find(identifier);
        String subject = loginCommand.email() != null ? loginCommand.email() : loginCommand.username();

        // Only an account that could sign in has a lock to report: the others answer as a wrong password does
        Optional<AppUser> eligible = account.filter(AppUser::canAuthenticate);
        if (eligible.isPresent() && eligible.get().isLoginTemporarilyLocked(now)) {
            log.warn("Login refused for user {}: temporarily locked", eligible.get().getId().getValue());
            audit(AuditEventType.LOGIN_BLOCKED, account, subject, "TEMPORARILY_LOCKED");
            throw new AccountTemporarilyLockedException(Duration.between(now, eligible.get().getLockedUntil()));
        }

        Authentication authentication;
        try {
            // Authenticate by Spring Security
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier.authenticationName(), loginCommand.password())
            );
        } catch (BadCredentialsException e) {
            // Unknown accounts leave nothing to count; the per-IP rate limit covers them
            audit(AuditEventType.LOGIN_FAILED, account, subject, "BAD_CREDENTIALS");
            eligible.ifPresent(user -> recordFailedAttempt(user, now));
            log.warn("Authenticate failed: bad credentials");
            throw new AuthenticationFailedException(REFUSED);
        } catch (AccountStatusException e) {
            log.warn("Authenticate failed: account is locked or disabled.");
            audit(AuditEventType.LOGIN_FAILED, account, subject, "ACCOUNT_DISABLED");
            throw new AuthenticationFailedException("Account is locked or disabled.");
        }

        AppUser appUser = lockedAccount(identifier, (VerifiedCredentials) authentication.getPrincipal()).orElseThrow(() -> {
            log.warn("Authenticate failed: the account may not sign in, or it changed during the password check");
            audit(AuditEventType.LOGIN_FAILED, account, subject, "ACCOUNT_REFUSED");
            return new AuthenticationFailedException(REFUSED);
        });
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // auth-email-access D4-10: an email sign-in proves the mailbox, so a PENDING account moves on; roles stay
        if (identifier instanceof LoginIdentifier.EmailLogin && appUser.activateIfPendingWithVerifiedEmail()) {
            loginAttemptRepository.activateIfPending(appUser.getId());
        }

        AuthResult result = loginInternal(appUser, clientInfo);
        audit(AuditEventType.LOGIN_SUCCEEDED, Optional.of(appUser), subject, null);
        return result;
    }

    /** The email policy, or the phone channel and its policy: a closed channel answers before any lookup. */
    private LoginIdentifier identifierOf(LoginCommand loginCommand) {
        if (loginCommand.email() != null) {
            return new LoginIdentifier.EmailLogin(Email.create(loginCommand.email()).getValue());
        }
        phoneChannel.require(PhoneChannelPolicy.Operation.SIGN_IN);
        return loginAccounts.phoneIdentifier(loginCommand.username());
    }

    /** The row under its lock, if it is still the account whose password was just checked, and it may sign in. */
    private Optional<AppUser> lockedAccount(LoginIdentifier identifier, VerifiedCredentials checked) {
        return userRepository.findByIdForSecurity(UserId.from(checked.userId()))
                .map(LockedUser::user)
                .filter(user -> user.getCredentialVersion() == checked.credentialVersion())
                .filter(AppUser::canAuthenticate)
                .filter(user -> loginAccounts.stillReaches(identifier, user));
    }

    @Transactional
    @Override
    public AuthResult loginInternal(AppUser appUser, ClientInfo clientInfo) {
        // Create access token
        String accessToken = accessTokenIssuer.issue(appUser);
        // Start a new sign-in session (refresh token family) for this device
        String refreshToken = refreshTokenGenerateUseCase.generateRefreshToken(appUser.getId(), clientInfo);

        // Record the login and forget earlier failed attempts, without writing the rest of the row
        loginAttemptRepository.recordLoginSuccess(appUser.getId(), AppClock.currentUTC());

        return AuthResult.of(appUser, accessToken, refreshToken, accountAccess.requiredAction(appUser));
    }

    private void recordFailedAttempt(AppUser user, Instant now) {
        int failedAttempts = loginAttemptRepository.incrementFailedLoginAttempts(user.getId());
        Optional<Duration> lock = lockoutPolicy.lockDurationAfter(failedAttempts);
        if (lock.isPresent()) {
            loginAttemptRepository.lockLoginUntil(user.getId(), now.plus(lock.get()));
            log.warn("User {} locked out for {} after {} failed login attempts",
                    user.getId().getValue(), lock.get(), failedAttempts);
            auditLog.record(AuthAuditEvent.of(AuditEventType.ACCOUNT_TEMPORARILY_LOCKED, user.getId().getValue(),
                    user.usernameValue(), "attempts=" + failedAttempts + ", lock=" + lock.get()));
            throw new AccountTemporarilyLockedException(lock.get());
        }
    }

    private void audit(AuditEventType type, Optional<AppUser> account, String subject, String details) {
        auditLog.record(AuthAuditEvent.of(type, account.map(u -> u.getId().getValue()).orElse(null), subject, details));
    }

}
