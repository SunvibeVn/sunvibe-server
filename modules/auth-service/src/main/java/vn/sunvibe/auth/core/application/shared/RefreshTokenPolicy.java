package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lifetimes that govern refresh tokens, from {@code app.jwt.*}.
 */
@Getter
@Component
public class RefreshTokenPolicy {

    /** A token not used for this long expires; each refresh restarts the clock. */
    private final Duration idleLifetime;
    /** A session ends this long after login, however active the user is. */
    private final Duration absoluteLifetime;
    /** How long after its rotation a used token may still be exchanged (concurrent tabs, retries). */
    private final Duration reuseGracePeriod;

    @Autowired
    public RefreshTokenPolicy(@Value("${app.jwt.jwtRefreshExpirationMs}") long idleLifetimeMs,
                              @Value("${app.jwt.refreshAbsoluteExpirationMs:2592000000}") long absoluteLifetimeMs,
                              @Value("${app.jwt.refreshReuseGraceSeconds:30}") long reuseGraceSeconds) {
        this(Duration.ofMillis(idleLifetimeMs), Duration.ofMillis(absoluteLifetimeMs), Duration.ofSeconds(reuseGraceSeconds));
    }

    public RefreshTokenPolicy(Duration idleLifetime, Duration absoluteLifetime, Duration reuseGracePeriod) {
        if (idleLifetime.isNegative() || idleLifetime.isZero()
                || absoluteLifetime.compareTo(idleLifetime) < 0 || reuseGracePeriod.isNegative()) {
            throw new IllegalArgumentException("Invalid refresh token lifetimes: idle=" + idleLifetime
                    + ", absolute=" + absoluteLifetime + ", reuseGrace=" + reuseGracePeriod);
        }
        this.idleLifetime = idleLifetime;
        this.absoluteLifetime = absoluteLifetime;
        this.reuseGracePeriod = reuseGracePeriod;
    }
}
