package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.auth.core.application.command.auth.ClientInfo;
import vn.sunvibe.auth.core.application.command.auth.TokenRefreshCommand;
import vn.sunvibe.auth.core.application.exception.auth.InvalidRefreshTokenException;
import vn.sunvibe.auth.core.application.query.auth.AuthResult;
import vn.sunvibe.auth.core.application.shared.RefreshTokenPolicy;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.auth.enums.TokenStatus;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain-JUnit test with hand-written fakes. Timestamps are relative to the real
 * clock because the service reads {@code AppClock}.
 */
class TokenRefreshServiceTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final String USERNAME = "0912345678";
    private static final String SECRET = "presented-refresh-secret";
    private static final Duration IDLE = Duration.ofDays(7);
    private static final Duration ABSOLUTE = Duration.ofDays(30);
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final ClientInfo DEVICE = new ClientInfo("Mozilla/5.0", "203.0.113.7");

    private InMemoryRefreshTokenRepository refreshTokens;
    private AppUser user;
    private Optional<AppUser> storedUser;
    private TokenRefreshService service;
    private RecordingAuditLog audit;

    @BeforeEach
    void setUp() {
        refreshTokens = new InMemoryRefreshTokenRepository();
        audit = new RecordingAuditLog();
        user = AppUser.create(USER_ID, "Nguyen Van A", Username.create(USERNAME), null,
                Password.create("hashed-password"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("TENANT")), Instant.now());
        storedUser = Optional.of(user);
        AtomicLong ids = new AtomicLong(100);
        service = new TokenRefreshService(refreshTokens, new UserLookup(),
                appUser -> "access-for-" + appUser.getUsername().getValue(), ids::incrementAndGet,
                new RefreshTokenPolicy(IDLE, ABSOLUTE, GRACE), audit);
    }

    private class UserLookup implements UserDomainRepository {
        @Override
        public boolean existsByUsername(String userName) { throw new UnsupportedOperationException(); }
        @Override
        public boolean existsByEmail(String email) { throw new UnsupportedOperationException(); }
        @Override
        public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { throw new UnsupportedOperationException(); }
        @Override
        public AppUser save(AppUser appUser) { throw new UnsupportedOperationException(); }
        @Override
        public AppUser findByUsername(String username) { throw new UnsupportedOperationException(); }
        @Override
        public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
        @Override
        public Optional<AppUser> findById(UserId userId) {
            return storedUser.filter(u -> u.getId().equals(userId));
        }

        @Override
        public boolean addRole(UserId userId, vn.sunvibe.auth.core.domain.model.user.enums.RoleName role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeRole(UserId userId, vn.sunvibe.auth.core.domain.model.user.enums.RoleName role) {
            throw new UnsupportedOperationException();
        }
    }

    /** The token the client presents, issued {@code age} ago at login. */
    private RefreshToken sessionStartedAgo(Duration age) {
        RefreshToken token = RefreshToken.startSession(1L, USER_ID, RefreshTokenSecrets.hash(SECRET),
                Instant.now().minus(age), IDLE, ABSOLUTE, "Mozilla/5.0", "203.0.113.7");
        refreshTokens.save(token);
        return token;
    }

    /** The presented token was already rotated {@code usedAgo} ago; its successor is still active. */
    private UUID rotatedAgo(Duration usedAgo) {
        UUID familyId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        Instant usedAt = Instant.now().minus(usedAgo);
        refreshTokens.save(RefreshToken.load(1L, USER_ID, familyId, RefreshTokenSecrets.hash(SECRET), issuedAt,
                issuedAt.plus(IDLE), issuedAt.plus(ABSOLUTE), null, null, TokenStatus.USED, usedAt, null, null));
        refreshTokens.save(RefreshToken.load(2L, USER_ID, familyId, "hash-of-successor", usedAt,
                usedAt.plus(IDLE), issuedAt.plus(ABSOLUTE), null, null, TokenStatus.ACTIVE, null, null, null));
        return familyId;
    }

    private AuthResult refresh(String secret) {
        return service.execute(new TokenRefreshCommand(secret, DEVICE));
    }

    private static String cookieValue(AuthResult result) {
        return result.refreshToken();
    }

    @Test
    void execute_rotatesTokenAndReturnsNewPair() {
        RefreshToken presented = sessionStartedAgo(Duration.ofHours(1));

        AuthResult result = refresh(SECRET);

        RefreshToken old = refreshTokens.get(1L);
        assertEquals(TokenStatus.USED, old.getStatus());
        assertNotNull(old.getUsedAt());

        String newSecret = cookieValue(result);
        assertNotEquals(SECRET, newSecret);
        RefreshToken next = refreshTokens.findByTokenHashForUpdate(RefreshTokenSecrets.hash(newSecret)).orElseThrow();
        assertTrue(next.isActive());
        assertEquals(presented.getFamilyId(), next.getFamilyId());
        assertEquals(presented.getSessionExpiresAt(), next.getSessionExpiresAt());
        assertEquals("Mozilla/5.0", next.getUserAgent());

        assertEquals("access-for-" + USERNAME, result.accessToken());
        assertEquals(USERNAME, result.username());
        assertEquals(Set.of("TENANT"), result.roles());
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void execute_newTokenCanBeRefreshedAgain() {
        sessionStartedAgo(Duration.ofHours(1));

        String second = cookieValue(refresh(SECRET));
        String third = cookieValue(refresh(second));

        assertNotEquals(second, third);
        assertEquals(3, refreshTokens.all().size());
        assertEquals(1, refreshTokens.all().stream().filter(RefreshToken::isActive).count());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void execute_rejectsMissingToken(String secret) {
        InvalidRefreshTokenException exception = assertThrows(InvalidRefreshTokenException.class, () -> refresh(secret));

        assertEquals(AuthMessage.SESSION_EXPIRED, exception.getMessage());
    }

    @Test
    void execute_rejectsUnknownToken() {
        sessionStartedAgo(Duration.ofHours(1));

        assertThrows(InvalidRefreshTokenException.class, () -> refresh("forged-secret"));
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void execute_rejectsTokenIdleLongerThanItsLifetime() {
        sessionStartedAgo(IDLE.plusMinutes(1));

        assertThrows(InvalidRefreshTokenException.class, () -> refresh(SECRET));
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void execute_rejectsTokenBeyondSessionAbsoluteLifetime() {
        Instant login = Instant.now().minus(ABSOLUTE).minusSeconds(1);
        Instant lastRefresh = Instant.now().minus(Duration.ofDays(1));
        refreshTokens.save(RefreshToken.load(1L, USER_ID, UUID.randomUUID(), RefreshTokenSecrets.hash(SECRET),
                lastRefresh, lastRefresh.plus(IDLE), login.plus(ABSOLUTE), null, null, TokenStatus.ACTIVE,
                null, null, null));

        assertThrows(InvalidRefreshTokenException.class, () -> refresh(SECRET));
    }

    @Test
    void execute_rejectsRevokedToken() {
        RefreshToken token = sessionStartedAgo(Duration.ofHours(1));
        refreshTokens.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, Instant.now());
        refreshTokens.revocations.clear();

        assertThrows(InvalidRefreshTokenException.class, () -> refresh(SECRET));
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    /** Two tabs refreshing with the same cookie at once must both stay signed in. */
    @Test
    void execute_toleratesReuseWithinGracePeriod() {
        UUID familyId = rotatedAgo(Duration.ofSeconds(5));

        AuthResult result = refresh(SECRET);

        RefreshToken sibling = refreshTokens.findByTokenHashForUpdate(RefreshTokenSecrets.hash(cookieValue(result))).orElseThrow();
        assertTrue(sibling.isActive());
        assertEquals(familyId, sibling.getFamilyId());
        assertTrue(refreshTokens.get(2L).isActive(), "the first successor stays valid too");
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    /** A rotated token showing up later means it was copied: end the whole session. */
    @Test
    void execute_reuseAfterGracePeriodRevokesTheWholeSession() {
        UUID familyId = rotatedAgo(GRACE.plusSeconds(60));

        InvalidRefreshTokenException exception = assertThrows(InvalidRefreshTokenException.class, () -> refresh(SECRET));

        assertEquals(AuthMessage.SESSION_EXPIRED, exception.getMessage());
        assertEquals(List.of("family:" + familyId + ":REUSE_DETECTED"), refreshTokens.revocations);
        assertTrue(refreshTokens.get(2L).isRevoked(), "the attacker's or victim's successor token is dead too");
        assertEquals(List.of(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED), audit.types());
        assertEquals("session=" + familyId, audit.last().details());
    }

    @Test
    void execute_lockedUserIsSignedOut() {
        RefreshToken token = sessionStartedAgo(Duration.ofHours(1));
        user.setUserStatus(UserStatus.LOCK);

        assertThrows(InvalidRefreshTokenException.class, () -> refresh(SECRET));
        assertEquals(List.of(AuditEventType.REFRESH_REJECTED_ACCOUNT_INACTIVE), audit.types());

        assertEquals(List.of("family:" + token.getFamilyId() + ":ACCOUNT_INACTIVE"), refreshTokens.revocations);
    }

    @Test
    void execute_deletedUserIsSignedOut() {
        RefreshToken token = sessionStartedAgo(Duration.ofHours(1));
        storedUser = Optional.empty();

        assertThrows(InvalidRefreshTokenException.class, () -> refresh(SECRET));

        assertEquals(List.of("family:" + token.getFamilyId() + ":ACCOUNT_INACTIVE"), refreshTokens.revocations);
    }
}
