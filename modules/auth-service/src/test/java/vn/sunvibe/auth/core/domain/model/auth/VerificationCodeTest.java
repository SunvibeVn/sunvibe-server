package vn.sunvibe.auth.core.domain.model.auth;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class VerificationCodeTest {

    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

    private static VerificationCode code() {
        return VerificationCode.issue(1L, "user-1", VerificationChannel.PHONE, "0912345678", "123456", NOW,
                Duration.ofMinutes(10));
    }

    @Test
    void issue_storesOnlyAHashOfTheCode() {
        VerificationCode code = code();

        assertNotEquals("123456", code.getCodeHash());
        assertFalse(code.getCodeHash().contains("123456"));
        assertEquals(64, code.getCodeHash().length());
    }

    @Test
    void sameCodeHashesDifferentlyPerRow() {
        VerificationCode other = VerificationCode.issue(2L, "user-1", VerificationChannel.PHONE, "0912345678", "123456",
                NOW, Duration.ofMinutes(10));

        assertNotEquals(code().getCodeHash(), other.getCodeHash());
    }

    @Test
    void matches_onlyTheRightCode() {
        VerificationCode code = code();

        assertTrue(code.matches("123456"));
        assertFalse(code.matches("123457"));
        assertFalse(code.matches(null));
    }

    @Test
    void isUsable_untilExpiryOrUse() {
        VerificationCode code = code();

        assertTrue(code.isUsable(NOW.plus(Duration.ofMinutes(9))));
        assertFalse(code.isUsable(NOW.plus(Duration.ofMinutes(10))));

        code.consume(NOW.plusSeconds(30));
        assertFalse(code.isUsable(NOW.plusSeconds(31)));
        assertThrows(IllegalStateException.class, () -> code.consume(NOW.plusSeconds(32)));
    }

    @Test
    void registerFailedAttempt_burnsAtTheLimit() {
        VerificationCode code = code();

        for (int i = 0; i < 4; i++) {
            assertFalse(code.registerFailedAttempt(5, NOW));
        }
        assertTrue(code.registerFailedAttempt(5, NOW));
        assertFalse(code.isUsable(NOW));
    }
}
