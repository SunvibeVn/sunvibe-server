package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetTokenTest {

    private static PasswordResetToken token() {
        return PasswordResetToken.create(1L, "user-1", VerificationChannel.EMAIL, "a@example.com", "123456");
    }

    @Test
    void create_isValidFor15Minutes() {
        PasswordResetToken token = token();

        assertTrue(token.isValid());
        assertEquals(PasswordResetToken.LIFETIME, java.time.Duration.between(token.getCreatedAt(), token.getExpiresAt()));
    }

    @Test
    void matchesOtp_onlyTheExactCode() {
        PasswordResetToken token = token();

        assertTrue(token.matchesOtp("123456"));
        assertFalse(token.matchesOtp("123457"));
        assertFalse(token.matchesOtp("12345"));
        assertFalse(token.matchesOtp(null));
    }

    @Test
    void registerFailedAttempt_burnsTheTokenAtTheLimit() {
        PasswordResetToken token = token();
        Instant now = Instant.now();

        for (int i = 0; i < 4; i++) {
            assertFalse(token.registerFailedAttempt(5, now));
        }
        assertTrue(token.registerFailedAttempt(5, now));

        assertFalse(token.isValid());
        assertEquals(now, token.getUsedAt());
        assertEquals(5, token.getFailedAttempts());
    }

    @Test
    void markAsUsed_onlyOnce() {
        PasswordResetToken token = token();
        token.markAsUsed(Instant.now());

        assertFalse(token.isValid());
        assertThrows(PasswordResetTokenException.class, () -> token.markAsUsed(Instant.now()));
    }
}
