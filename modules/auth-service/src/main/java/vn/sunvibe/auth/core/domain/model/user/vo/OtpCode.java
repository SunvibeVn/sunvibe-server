package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;

import java.security.SecureRandom;

/**
 * A freshly generated 6-digit one-time code, sent by SMS or e-mail.
 * <p>
 * Only generation lives here. Where the code is stored, how long it lives and
 * how many wrong guesses it tolerates is decided by the token that carries it
 * ({@link PasswordResetToken}, {@code VerificationCode}).
 */
@Getter
public final class OtpCode {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Always six digits, never with a leading zero (100000-999999). */
    private final String code;

    private OtpCode(String code) {
        this.code = code;
    }

    /** Generates a new random code from a cryptographically strong source. */
    public static OtpCode create() {
        return new OtpCode(String.valueOf(100_000 + SECURE_RANDOM.nextInt(900_000)));
    }

}
