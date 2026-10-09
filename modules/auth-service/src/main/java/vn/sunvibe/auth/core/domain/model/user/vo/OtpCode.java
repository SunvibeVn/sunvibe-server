package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Builder;
import lombok.Getter;
import vn.sunvibe.auth.core.domain.exception.auth.InvalidOtpException;
import vn.sunvibe.auth.core.domain.exception.auth.OtpAlreadyUsedException;
import vn.sunvibe.auth.core.infrastructure.time.Clock;
import vn.sunvibe.common.time.AppClock;

import java.time.Instant;
import java.util.Random;

@Builder
@Getter
public class OtpCode {

    private static final int OTP_LENGTH = 6;
    private static final String OTP_PATTERN = "^[0-9]{6}$";

    String code;
    Instant createdAt;
    Instant expiresAt;
    boolean used;

    private OtpCode(String code, Instant createdAt, Instant expiresAt, boolean used) {
        this.code = code;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.used = used;
    }

    // Factory method - generate new OTP
    public static OtpCode create() {
        String code = generateRandomOtp();
        validateOtpCode(code);
        Instant createdAt = AppClock.currentUTC();
        Instant expiresAt = createdAt.plusSeconds(900);  // 15 mins expiration

        return new OtpCode(code, createdAt, expiresAt, false);
    }

    private static String generateRandomOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    // Factory method - restore from DB
    public static OtpCode restore(String code, Instant createdAt, Instant expiresAt, boolean used) {
        return new OtpCode(code, createdAt, expiresAt, used);
    }

    private static void validateOtpCode(String code) {
        if (code == null || !code.matches(OTP_PATTERN)) {
            throw new InvalidOtpException("OTP must be 6 digits");
        }
    }

    // Business logic
    public boolean isExpired() {
        return AppClock.currentUTC().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return this.used;
    }

    public boolean matches(String code) {
        return this.code != null && this.code.equals(code);
    }

    public void markAsUsed() {
        if (this.used) {
            throw new OtpAlreadyUsedException("This OTP has already been used");
        }
        this.used = true;
    }

}
