package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limits of phone/e-mail verification codes, from {@code app.security.verification.*}.
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

    @Autowired
    public VerificationPolicy(@Value("${app.security.verification.code-lifetime:10m}") Duration codeLifetime,
                              @Value("${app.security.verification.max-attempts:5}") int maxAttempts,
                              @Value("${app.security.verification.resend-cooldown:60s}") Duration resendCooldown,
                              @Value("${app.security.verification.max-codes-per-day:5}") int maxCodesPerDay) {
        if (codeLifetime.isNegative() || codeLifetime.isZero() || maxAttempts < 1 || resendCooldown.isNegative()
                || maxCodesPerDay < 1) {
            throw new IllegalArgumentException("Invalid verification policy: lifetime=" + codeLifetime
                    + ", maxAttempts=" + maxAttempts + ", cooldown=" + resendCooldown + ", perDay=" + maxCodesPerDay);
        }
        this.codeLifetime = codeLifetime;
        this.maxAttempts = maxAttempts;
        this.resendCooldown = resendCooldown;
        this.maxCodesPerDay = maxCodesPerDay;
    }
}
