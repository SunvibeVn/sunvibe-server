package vn.sunvibe.auth.core.application.service.auth;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.SignedIn;
import vn.sunvibe.auth.core.testsupport.UnsupportedUserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LogoutServiceTest {

    private static final UserId USER_ID = UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV");
    private static final String USERNAME = "0912345678";

    private AppUser user;
    private final List<String> calls = new java.util.ArrayList<>();
    private InMemoryRefreshTokenRepository refreshTokens;
    private LogoutService service;
    private final RecordingAuditLog audit = new RecordingAuditLog();

    @BeforeEach
    void setUp() {
        refreshTokens = new InMemoryRefreshTokenRepository();
        user = AuthTestUsers.withPhone(USER_ID, "Nguyen Van A", USERNAME, null,
                Password.create("hashed-password"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("TENANT")), Instant.now());
        refreshTokens.calls = calls;
        service = new LogoutService(refreshTokens, new UnsupportedUserRepository() {
            @Override
            public Optional<LockedUser> findByIdForSecurity(UserId userId) {
                assertEquals(USER_ID, userId);
                calls.add("lock-user");
                return Optional.of(new LockedUser(user));
            }
        }, audit);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private RefreshToken session(long id, String secret) {
        RefreshToken token = RefreshToken.startSession(id, USER_ID, RefreshTokenSecrets.hash(secret), Instant.now(),
                Duration.ofDays(7), Duration.ofDays(30), null, null);
        refreshTokens.save(token);
        return token;
    }

    @Test
    void execute_endsOnlyThisDevicesSession() {
        RefreshToken thisDevice = session(1L, "phone-secret");
        session(2L, "laptop-secret");

        service.execute("phone-secret");

        assertEquals(List.of("family:" + thisDevice.getFamilyId() + ":LOGOUT"), refreshTokens.revocations);
        assertTrue(refreshTokens.get(1L).isRevoked());
        assertTrue(refreshTokens.get(2L).isActive());
        assertEquals(List.of(AuditEventType.LOGOUT), audit.types());
        assertEquals(USER_ID.getValue(), audit.last().userId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown-secret"})
    void execute_isANoOpWithoutAKnownToken(String secret) {
        session(1L, "phone-secret");

        assertDoesNotThrow(() -> service.execute(secret));
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void execute_twiceIsHarmless() {
        session(1L, "phone-secret");
        service.execute("phone-secret");

        assertDoesNotThrow(() -> service.execute("phone-secret"));
        assertEquals(1, refreshTokens.revocations.size());
    }

    @Test
    void executeAllDevices_endsEverySessionOfTheSignedInUser() {
        session(1L, "phone-secret");
        session(2L, "laptop-secret");
        SignedIn.as(USER_ID.getValue());

        service.executeAllDevices();

        assertEquals(List.of("user:" + USER_ID.getValue() + ":" + RevokeReason.LOGOUT_ALL), refreshTokens.revocations);
        assertTrue(refreshTokens.all().stream().allMatch(RefreshToken::isRevoked));
        assertEquals(List.of(AuditEventType.LOGOUT_ALL_DEVICES), audit.types());
    }

    /** auth-email-access spec 5.4: user row first, or logout deadlocks with a reset on the family rows. */
    @Test
    void logout_locksUserBeforeToken() {
        session(1L, "phone-secret");

        service.execute("phone-secret");

        assertEquals(List.of("read-token-owner", "lock-user", "lock-token"), calls);
    }

    @Test
    void logoutAll_locksUser() {
        session(1L, "phone-secret");
        SignedIn.as(USER_ID.getValue());

        service.executeAllDevices();

        assertEquals(List.of("lock-user"), calls);
    }

    /** auth-email-access S-20: an account without a phone. */
    @Test
    void logoutAll_nullUsername() {
        user = AppUser.registerWithEmail(USER_ID, "Nguyen Van A", Email.create("a@example.com"),
                Password.create("hashed-password"), new HashSet<>(Set.of("TENANT")), Instant.now());
        session(1L, "laptop-secret");
        SignedIn.as(USER_ID.getValue());

        service.executeAllDevices();

        assertEquals(List.of("user:" + USER_ID.getValue() + ":" + RevokeReason.LOGOUT_ALL), refreshTokens.revocations);
    }

    @Test
    void executeAllDevices_requiresAuthentication() {
        assertThrows(InsufficientAuthenticationException.class, () -> service.executeAllDevices());
    }
}
