package vn.sunvibe.auth.core.application.usecase.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.shared.LoginLockoutPolicy;
import vn.sunvibe.auth.core.application.usecase.user.PasswordReauthenticationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserLoginAttemptRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Checks the password without a row lock, then writes the counter with a guarded update, last, so the lock of that update
 * is held only until the commit that follows at once.
 */
@Service
@RequiredArgsConstructor
public class PasswordReauthenticationService implements PasswordReauthenticationUseCase {

    private final UserDomainRepository userRepository;
    private final UserLoginAttemptRepository attempts;
    private final PasswordEncoder passwordEncoder;
    private final LoginLockoutPolicy lockoutPolicy;
    private final TransactionTemplate transaction;

    @Override
    public ReauthenticationResult check(String userId, String password) {
        return transaction.execute(status -> checkInTransaction(UserId.from(userId), password));
    }

    private ReauthenticationResult checkInTransaction(UserId userId, String password) {
        Optional<AppUser> account = userRepository.findById(userId);
        if (account.isEmpty()) {
            return ReauthenticationResult.wrong();
        }
        AppUser user = account.get();
        Instant now = AppClock.currentUTC();
        if (user.getReauthLockedUntil() != null && now.isBefore(user.getReauthLockedUntil())) {
            return ReauthenticationResult.locked(Duration.between(now, user.getReauthLockedUntil()));
        }

        if (!passwordEncoder.matches(password, user.getPassword().getHashedValue())) {
            return countWrong(userId, now);
        }
        if (user.getReauthFailedAttempts() > 0 || user.getReauthLockedUntil() != null) {
            attempts.clearReauthFailures(userId);
        }
        return ReauthenticationResult.ok(user.getCredentialVersion());
    }

    private ReauthenticationResult countWrong(UserId userId, Instant now) {
        Optional<Duration> lock = lockoutPolicy.lockDurationAfter(attempts.incrementReauthFailures(userId));
        if (lock.isEmpty()) {
            return ReauthenticationResult.wrong();
        }
        attempts.lockReauthUntil(userId, now.plus(lock.get()));
        return ReauthenticationResult.locked(lock.get());
    }
}
