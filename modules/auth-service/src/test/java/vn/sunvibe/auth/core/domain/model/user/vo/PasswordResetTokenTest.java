package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.auth.PasswordResetTokenException;
import vn.sunvibe.auth.core.domain.model.auth.enums.VerificationChannel;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetTokenTest {

    private static PasswordResetToken token() {
        return PasswordResetToken.issue(1L, "user-1", VerificationChannel.EMAIL, "a@example.com", "123456", 3L).token();
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

    /** SV-096: the value goes to the user; the row keeps what a reader of the table cannot use. */
    @Test
    void issue_keepsOnlyTheHashOfTheToken() {
        PasswordResetToken.Issued issued = PasswordResetToken.issue(1L, "user-1", VerificationChannel.EMAIL, "a@example.com",
                "123456", 3L);

        assertNotEquals(issued.plainToken(), issued.token().getToken());
        assertEquals(PasswordResetToken.hashToken(issued.plainToken()), issued.token().getToken());
        assertTrue(issued.token().getToken().matches("[0-9a-f]{64}"));
        assertFalse(issued.toString().contains(issued.plainToken()));
    }

    @Test
    void isBoundTo_sameVersionAndAddress() {
        assertTrue(token().isBoundTo(3L, "a@example.com"));
    }

    @Test
    void isBoundTo_otherVersion_false() {
        assertFalse(token().isBoundTo(4L, "a@example.com"));
    }

    @Test
    void isBoundTo_otherAddress_false() {
        assertFalse(token().isBoundTo(3L, "b@example.com"));
        assertFalse(token().isBoundTo(3L, null));
    }

    /** The address of a phone token is no matter: the account may have none. */
    @Test
    void isBoundTo_phone_comparesTheVersionOnly() {
        PasswordResetToken phone = PasswordResetToken.issue(1L, "user-1", VerificationChannel.PHONE, "+84912345678", "123456",
                3L).token();

        assertTrue(phone.isBoundTo(3L, null));
        assertFalse(phone.isBoundTo(4L, null));
    }

    @Test
    void isBoundTo_rowWithoutVersion_false() {
        Instant now = Instant.now();
        PasswordResetToken old = PasswordResetToken.restore(1L, "hash", "user-1", VerificationChannel.EMAIL, "a@example.com",
                now, now.plusSeconds(900), false, null, 0, "123456", null);

        assertFalse(old.isBoundTo(0L, "a@example.com"));
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
