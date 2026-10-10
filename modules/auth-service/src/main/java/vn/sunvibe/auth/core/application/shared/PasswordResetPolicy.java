package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limits of the OTP password reset (e-mail or SMS), from {@code app.security.password-reset.*}.
 */
@Getter
@Component
public class PasswordResetPolicy {

    /** Wrong OTP codes a reset token tolerates before it is burned. */
    private final int maxOtpAttempts;
    /** Minimum time between two reset codes to the same e-mail/phone. */
    private final Duration resendCooldown;
    /**
     * Reset codes per e-mail/phone per 24 h. With 5 guesses per code this caps
     * blind OTP guessing at 25 tries a day per account.
     */
    private final int maxCodesPerDay;

    @Autowired
    public PasswordResetPolicy(@Value("${app.security.password-reset.max-otp-attempts:5}") int maxOtpAttempts,
                               @Value("${app.security.password-reset.resend-cooldown:60s}") Duration resendCooldown,
                               @Value("${app.security.password-reset.max-codes-per-day:5}") int maxCodesPerDay) {
        if (maxOtpAttempts < 1 || resendCooldown.isNegative() || maxCodesPerDay < 1) {
            throw new IllegalArgumentException("Invalid password reset policy: maxOtpAttempts=" + maxOtpAttempts
                    + ", resendCooldown=" + resendCooldown + ", maxCodesPerDay=" + maxCodesPerDay);
        }
        this.maxOtpAttempts = maxOtpAttempts;
        this.resendCooldown = resendCooldown;
        this.maxCodesPerDay = maxCodesPerDay;
    }

    public PasswordResetPolicy(int maxOtpAttempts, Duration resendCooldown) {
        this(maxOtpAttempts, resendCooldown, 5);
    }
}
