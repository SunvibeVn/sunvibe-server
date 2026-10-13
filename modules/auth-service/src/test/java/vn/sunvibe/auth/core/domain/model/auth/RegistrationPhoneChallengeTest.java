package vn.sunvibe.auth.core.domain.model.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationPhoneChallengeTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private RegistrationPhoneChallenge issue(String code) {
        return RegistrationPhoneChallenge.issue(7L, "0912345678", code, NOW, CODE_LIFETIME);
    }

    @Test
    void issue_storesOnlyAHashOfTheCode() {
        RegistrationPhoneChallenge challenge = issue("123456");

        assertNotEquals("123456", challenge.getCodeHash());
        assertTrue(challenge.matches("123456"));
        assertFalse(challenge.matches("654321"));
        assertFalse(challenge.matches(null));
        assertEquals(NOW.plus(CODE_LIFETIME), challenge.getExpiresAt());
    }

    @Test
    void sameCodeHashesDifferentlyPerChallenge() {
        RegistrationPhoneChallenge other = RegistrationPhoneChallenge.issue(8L, "0912345678", "123456", NOW, CODE_LIFETIME);

        assertNotEquals(issue("123456").getCodeHash(), other.getCodeHash());
    }

    @Test
    void isUsable_untilExpiryOrConsumption() {
        RegistrationPhoneChallenge challenge = issue("123456");

        assertTrue(challenge.isUsable(NOW.plusSeconds(1)));
        assertFalse(challenge.isUsable(NOW.plus(CODE_LIFETIME)));
    }

    @Test
    void registerFailedAttempt_theLastAllowedOneBurnsTheCode() {
        RegistrationPhoneChallenge challenge = issue("123456");

        assertFalse(challenge.registerFailedAttempt(3, NOW));
        assertFalse(challenge.registerFailedAttempt(3, NOW));
        assertTrue(challenge.registerFailedAttempt(3, NOW));
        assertFalse(challenge.isUsable(NOW));
        assertEquals(3, challenge.getFailedAttempts());
    }

    @Test
    void verify_usesTheCodeUpAndAttachesAHashedToken() {
        RegistrationPhoneChallenge challenge = issue("123456");

        challenge.verify("the-token", NOW.plusSeconds(30), TOKEN_LIFETIME);

        assertFalse(challenge.isUsable(NOW.plusSeconds(31)), "a code works once");
        assertEquals(RegistrationPhoneChallenge.hashToken("the-token"), challenge.getTokenHash());
        assertNotEquals("the-token", challenge.getTokenHash());
        assertEquals(NOW.plusSeconds(30).plus(TOKEN_LIFETIME), challenge.getTokenExpiresAt());
        assertTrue(challenge.isTokenUsable(NOW.plusSeconds(31)));
    }

    @Test
    void verify_refusesAnUnusableCode() {
        RegistrationPhoneChallenge expired = issue("123456");

        assertThrows(IllegalStateException.class,
                () -> expired.verify("t", NOW.plus(CODE_LIFETIME), TOKEN_LIFETIME));
    }

    @Test
    void token_isUsableOnceAndOnlyBeforeItExpires() {
        RegistrationPhoneChallenge challenge = issue("123456");
        challenge.verify("the-token", NOW, TOKEN_LIFETIME);

        assertFalse(challenge.isTokenUsable(NOW.plus(TOKEN_LIFETIME)));
        challenge.markRegistered(NOW.plusSeconds(60));

        assertFalse(challenge.isTokenUsable(NOW.plusSeconds(61)));
        assertThrows(IllegalStateException.class, () -> challenge.markRegistered(NOW.plusSeconds(62)));
    }

    @Test
    void markRegistered_requiresAVerifiedChallenge() {
        assertThrows(IllegalStateException.class, () -> issue("123456").markRegistered(NOW));
    }
}
