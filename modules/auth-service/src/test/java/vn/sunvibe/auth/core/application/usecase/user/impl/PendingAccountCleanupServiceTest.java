package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PendingAccountCleanupServiceTest {

    private InMemoryUserRepository users;
    private InMemoryRefreshTokenRepository refreshTokens;
    private RecordingAuditLog audit;
    private PendingAccountCleanupService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        audit = new RecordingAuditLog();
        service = new PendingAccountCleanupService(users, users, refreshTokens,
                new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5, Duration.ofDays(7)), audit);
    }

    private AppUser registeredAgo(String id, String phone, Duration age) {
        AppUser user = AppUser.create(UserId.create(id), "Nguyen Van A", Username.create(phone), null,
                Password.create("hashed"), ProfileImage.create(null), null, new HashSet<>(Set.of("TENANT")),
                Instant.now().minus(age));
        users.with(user);
        return user;
    }

    @Test
    void deactivatesAccountsUnverifiedForMoreThanSevenDays() {
        AppUser expired = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA1", "0911111111", Duration.ofDays(8));
        refreshTokens.save(RefreshToken.startSession(1L, expired.getId(), "hash-1", Instant.now(),
                Duration.ofDays(7), Duration.ofDays(30), null, null));

        int deactivated = service.execute();

        assertEquals(1, deactivated);
        assertEquals(UserStatus.DELETED, expired.getUserStatus());
        assertNotNull(expired.getDeletedAt(), "soft delete frees the phone number for its real owner");
        assertTrue(refreshTokens.get(1L).isRevoked());
        assertEquals(List.of(AuditEventType.PENDING_ACCOUNT_EXPIRED), audit.types());
        assertEquals("0911111111", audit.last().subject());
    }

    @Test
    void keepsAccountsStillWithinTheirSevenDays() {
        AppUser recent = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA2", "0922222222", Duration.ofDays(6));

        assertEquals(0, service.execute());
        assertEquals(UserStatus.PENDING, recent.getUserStatus());
    }

    @Test
    void keepsVerifiedAccounts() {
        AppUser verified = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA3", "0933333333", Duration.ofDays(30));
        verified.verifyPhone(Instant.now().minus(Duration.ofDays(29)));

        assertEquals(0, service.execute());
        assertEquals(UserStatus.ACTIVE, verified.getUserStatus());
    }

    /** Accounts created before phone verification existed are ACTIVE, not PENDING: left alone. */
    @Test
    void keepsOldActiveAccountsThatWereNeverAskedToVerify() {
        AppUser old = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA4", "0944444444", Duration.ofDays(60));
        old.setUserStatus(UserStatus.ACTIVE);

        assertEquals(0, service.execute());
        assertEquals(UserStatus.ACTIVE, old.getUserStatus());
        assertFalse(old.isPhoneVerified());
    }
}
