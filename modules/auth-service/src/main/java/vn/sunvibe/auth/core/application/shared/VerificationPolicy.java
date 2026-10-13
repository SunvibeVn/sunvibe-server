package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limits of phone/e-mail verification, from {@code app.security.verification.*}.
 */
@Getter
@Component
public class VerificationPolicy {

    private final Duration codeLifetime;
    /** Wrong codes tolerated before the code is burned. */
    private final int maxAttempts;
    /** Minimum time between two codes on the same channel. */
    private final Duration resendCooldown;
    /** Codes per account and channel per 24 hours (SMS costs money and annoys people). */
    private final int maxCodesPerDay;
    /** A PENDING account whose phone is still unverified this long after registration is deactivated. */
    private final Duration pendingAccountTtl;
    /** How long a registration token (issued after the phone was verified) may be presented to registration. */
    private final Duration registrationTokenLifetime;

    @Autowired
    public VerificationPolicy(@Value("${app.security.verification.code-lifetime:10m}") Duration codeLifetime,
                              @Value("${app.security.verification.max-attempts:5}") int maxAttempts,
                              @Value("${app.security.verification.resend-cooldown:60s}") Duration resendCooldown,
                              @Value("${app.security.verification.max-codes-per-day:5}") int maxCodesPerDay,
                              @Value("${app.security.verification.pending-account-ttl:7d}") Duration pendingAccountTtl,
                              @Value("${app.security.verification.registration-token-lifetime:15m}") Duration registrationTokenLifetime) {
        if (codeLifetime.isNegative() || codeLifetime.isZero() || maxAttempts < 1 || resendCooldown.isNegative()
                || maxCodesPerDay < 1 || pendingAccountTtl.isNegative() || pendingAccountTtl.isZero()
                || registrationTokenLifetime.isNegative() || registrationTokenLifetime.isZero()) {
            throw new IllegalArgumentException("Invalid verification policy: lifetime=" + codeLifetime
                    + ", maxAttempts=" + maxAttempts + ", cooldown=" + resendCooldown + ", perDay=" + maxCodesPerDay
                    + ", pendingAccountTtl=" + pendingAccountTtl
                    + ", registrationTokenLifetime=" + registrationTokenLifetime);
        }
        this.codeLifetime = codeLifetime;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
        this.maxCodesPerDay = maxCodesPerDay;
        this.pendingAccountTtl = pendingAccountTtl;
        this.registrationTokenLifetime = registrationTokenLifetime;
    }

    public VerificationPolicy(Duration codeLifetime, int maxAttempts, Duration resendCooldown, int maxCodesPerDay,
                              Duration pendingAccountTtl) {
        this(codeLifetime, maxAttempts, resendCooldown, maxCodesPerDay, pendingAccountTtl, Duration.ofMinutes(15));
    }

    public VerificationPolicy(Duration codeLifetime, int maxAttempts, Duration resendCooldown, int maxCodesPerDay) {
        this(codeLifetime, maxAttempts, resendCooldown, maxCodesPerDay, Duration.ofDays(7));
    }
}
