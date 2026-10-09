package vn.sunvibe.auth.core.application.shared;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Limits of the e-mail OTP password reset, from {@code app.security.password-reset.*}.
 */
@Getter
@Component
public class PasswordResetPolicy {

    /** Wrong OTP codes a reset token tolerates before it is burned. */
    private final int maxOtpAttempts;
    /** Minimum time between two reset e-mails to the same address. */
    private final Duration resendCooldown;

    @Autowired
    public PasswordResetPolicy(@Value("${app.security.password-reset.max-otp-attempts:5}") int maxOtpAttempts,
                               @Value("${app.security.password-reset.resend-cooldown:60s}") Duration resendCooldown) {
        if (maxOtpAttempts < 1 || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("Invalid password reset policy: maxOtpAttempts=" + maxOtpAttempts
                    + ", resendCooldown=" + resendCooldown);
        }
        this.maxOtpAttempts = maxOtpAttempts;
        this.resendCooldown = resendCooldown;
    }
}
