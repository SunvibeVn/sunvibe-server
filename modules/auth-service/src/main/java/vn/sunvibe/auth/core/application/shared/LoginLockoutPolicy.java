package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Progressive lockout after wrong passwords, from {@code app.security.login.*}.
 * <p>
 * Every {@link #maxFailedAttempts}-th consecutive failure locks password login:
 * first for {@link #baseLockDuration}, then twice as long each further round,
 * capped at {@link #maxLockDuration} (defaults: 5 attempts, 15 min, 30 min,
 * 1 h, ... up to 24 h). A successful login or a password reset starts over.
 */
@Getter
@Component
public class LoginLockoutPolicy {

    private final int maxFailedAttempts;
    private final Duration baseLockDuration;
    private final Duration maxLockDuration;

    @Autowired
    public LoginLockoutPolicy(@Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
                              @Value("${app.security.login.lock-duration:15m}") Duration baseLockDuration,
                              @Value("${app.security.login.max-lock-duration:24h}") Duration maxLockDuration) {
        if (maxFailedAttempts < 1 || baseLockDuration.isNegative() || baseLockDuration.isZero()
                || maxLockDuration.compareTo(baseLockDuration) < 0) {
            throw new IllegalArgumentException("Invalid login lockout policy: attempts=" + maxFailedAttempts
                    + ", lock=" + baseLockDuration + ", maxLock=" + maxLockDuration);
        }
        this.maxFailedAttempts = maxFailedAttempts;
        this.baseLockDuration = baseLockDuration;
        this.maxLockDuration = maxLockDuration;
    }

    /**
     * How long to lock after reaching {@code failedAttempts} consecutive failures,
     * or empty if this count does not trigger a lock.
     */
    public Optional<Duration> lockDurationAfter(int failedAttempts) {
        if (failedAttempts < maxFailedAttempts || failedAttempts % maxFailedAttempts != 0) {
            return Optional.empty();
        }
        int round = failedAttempts / maxFailedAttempts;
        Duration duration = baseLockDuration.multipliedBy(1L << Math.min(round - 1, 20));
        return Optional.of(duration.compareTo(maxLockDuration) > 0 ? maxLockDuration : duration);
    }
}
