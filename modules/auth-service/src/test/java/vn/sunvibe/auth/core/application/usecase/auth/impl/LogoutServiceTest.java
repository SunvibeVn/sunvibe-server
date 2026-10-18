package vn.sunvibe.auth.core.application.usecase.auth.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.application.shared.RefreshTokenSecrets;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

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

    private InMemoryRefreshTokenRepository refreshTokens;
    private LogoutService service;
    private final RecordingAuditLog audit = new RecordingAuditLog();

    @BeforeEach
    void setUp() {
        refreshTokens = new InMemoryRefreshTokenRepository();
        AppUser user = AppUser.create(USER_ID, "Nguyen Van A", Username.create(USERNAME), null,
                Password.create("hashed-password"), ProfileImage.create(null), null,
                new HashSet<>(Set.of("TENANT")), Instant.now());
        service = new LogoutService(refreshTokens, new UserDomainRepository() {
            @Override
            public boolean existsByUsername(String userName) { throw new UnsupportedOperationException(); }
            @Override
            public boolean existsByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public boolean existsByIdCardNumber(IdCardNumber idCardNumber) { throw new UnsupportedOperationException(); }
            @Override
            public AppUser save(AppUser appUser) { throw new UnsupportedOperationException(); }
            @Override
            public AppUser findByUsername(String username) {
                assertEquals(USERNAME, username);
                return user;
            }
            @Override
            public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
            @Override
            public Optional<AppUser> findById(UserId userId) { throw new UnsupportedOperationException(); }
            @Override public boolean addRole(UserId userId, vn.sunvibe.auth.core.domain.model.user.enums.RoleName role) { throw new UnsupportedOperationException(); }
            @Override public boolean removeRole(UserId userId, vn.sunvibe.auth.core.domain.model.user.enums.RoleName role) { throw new UnsupportedOperationException(); }
            @Override public java.util.List<vn.sunvibe.auth.core.domain.repository.user.StaffAccountRow> findSignInAbleByAnyRole(java.util.Set<String> roleNames, int limit) { throw new UnsupportedOperationException(); }
            @Override public java.util.Map<String, String> findFullNamesByIds(java.util.Collection<String> ids) { throw new UnsupportedOperationException(); }
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
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USERNAME, null, List.of()));

        service.executeAllDevices();

        assertEquals(List.of("user:" + USER_ID.getValue() + ":" + RevokeReason.LOGOUT_ALL), refreshTokens.revocations);
        assertTrue(refreshTokens.all().stream().allMatch(RefreshToken::isRevoked));
        assertEquals(List.of(AuditEventType.LOGOUT_ALL_DEVICES), audit.types());
    }

    @Test
    void executeAllDevices_requiresAuthentication() {
        assertThrows(InsufficientAuthenticationException.class, () -> service.executeAllDevices());
    }
}
