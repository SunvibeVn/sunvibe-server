package vn.sunvibe.auth.core.domain.model.auth;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final Duration IDLE = Duration.ofDays(7);
    private static final Duration ABSOLUTE = Duration.ofDays(30);
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final Instant LOGIN = Instant.parse("2026-10-01T08:00:00Z");

    private static RefreshToken loginToken() {
        return RefreshToken.startSession(1L, USER_ID, "hash-1", LOGIN, IDLE, ABSOLUTE, "Mozilla/5.0", "203.0.113.7");
    }

    @Test
    void startSession_isActiveWithIdleExpiryAndAbsoluteSessionExpiry() {
        RefreshToken token = loginToken();

        assertTrue(token.isActive());
        assertEquals(LOGIN.plus(IDLE), token.getExpiresAt());
        assertEquals(LOGIN.plus(ABSOLUTE), token.getSessionExpiresAt());
        assertNotNull(token.getFamilyId());
    }

    @Test
    void startSession_eachLoginIsANewSession() {
        assertNotEquals(loginToken().getFamilyId(), loginToken().getFamilyId());
    }

    @Test
    void rotate_marksTokenUsedAndIssuesActiveSuccessorInSameSession() {
        RefreshToken token = loginToken();
        Instant now = LOGIN.plus(Duration.ofHours(1));

        RefreshToken next = token.rotate(2L, "hash-2", now, IDLE, "Mobile", "198.51.100.1");

        assertEquals(TokenStatus.USED, token.getStatus());
        assertEquals(now, token.getUsedAt());
        assertTrue(next.isActive());
        assertEquals(token.getFamilyId(), next.getFamilyId());
        assertEquals(now.plus(IDLE), next.getExpiresAt(), "each refresh restarts the idle lifetime");
        assertEquals(token.getSessionExpiresAt(), next.getSessionExpiresAt());
        assertEquals("Mobile", next.getUserAgent());
    }

    @Test
    void rotate_neverExtendsBeyondTheSessionAbsoluteExpiry() {
        Instant nearSessionEnd = LOGIN.plus(ABSOLUTE).minus(Duration.ofDays(1));

        RefreshToken next = loginToken().rotate(2L, "hash-2", LOGIN.plus(Duration.ofDays(6)), IDLE, null, null)
                .rotate(3L, "hash-3", LOGIN.plus(Duration.ofDays(12)), IDLE, null, null)
                .rotate(4L, "hash-4", LOGIN.plus(Duration.ofDays(18)), IDLE, null, null)
                .rotate(5L, "hash-5", LOGIN.plus(Duration.ofDays(24)), IDLE, null, null)
                .rotate(6L, "hash-6", nearSessionEnd, IDLE, null, null);

        assertEquals(LOGIN.plus(ABSOLUTE), next.getExpiresAt());
        assertTrue(next.isExpired(LOGIN.plus(ABSOLUTE)));
    }

    @Test
    void rotate_withinGraceKeepsTheFirstUsedAt() {
        RefreshToken token = loginToken();
        Instant firstUse = LOGIN.plus(Duration.ofHours(1));
        token.rotate(2L, "hash-2", firstUse, IDLE, null, null);

        RefreshToken sibling = token.rotate(3L, "hash-3", firstUse.plusSeconds(5), IDLE, null, null);

        assertEquals(firstUse, token.getUsedAt());
        assertTrue(sibling.isActive());
    }

    @Test
    void rotate_rejectsExpiredToken() {
        RefreshToken token = loginToken();

        assertThrows(IllegalStateException.class,
                () -> token.rotate(2L, "hash-2", LOGIN.plus(IDLE), IDLE, null, null));
    }

    @Test
    void rotate_rejectsRevokedToken() {
        RefreshToken revoked = RefreshToken.load(1L, USER_ID, loginToken().getFamilyId(), "hash-1", LOGIN,
                LOGIN.plus(IDLE), LOGIN.plus(ABSOLUTE), null, null, TokenStatus.REVOKED, null, LOGIN,
                RevokeReason.LOGOUT);

        assertThrows(IllegalStateException.class,
                () -> revoked.rotate(2L, "hash-2", LOGIN.plusSeconds(1), IDLE, null, null));
    }

    @Test
    void isExpired_atIdleExpiry() {
        RefreshToken token = loginToken();

        assertFalse(token.isExpired(LOGIN.plus(IDLE).minusSeconds(1)));
        assertTrue(token.isExpired(LOGIN.plus(IDLE)));
    }

    @Test
    void isWithinReuseGrace_onlyForUsedTokensShortlyAfterRotation() {
        RefreshToken token = loginToken();
        Instant firstUse = LOGIN.plus(Duration.ofHours(1));
        assertFalse(token.isWithinReuseGrace(firstUse, GRACE), "an active token is not a reuse");

        token.rotate(2L, "hash-2", firstUse, IDLE, null, null);

        assertTrue(token.isWithinReuseGrace(firstUse.plus(GRACE), GRACE));
        assertFalse(token.isWithinReuseGrace(firstUse.plus(GRACE).plusSeconds(1), GRACE));
    }

    @Test
    void load_requiresATokenHash() {
        assertThrows(IllegalArgumentException.class, () -> RefreshToken.startSession(1L, USER_ID, " ", LOGIN,
                IDLE, ABSOLUTE, null, null));
    }
}
