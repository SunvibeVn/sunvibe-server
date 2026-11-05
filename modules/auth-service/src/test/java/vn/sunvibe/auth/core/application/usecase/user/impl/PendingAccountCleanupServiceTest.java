package vn.sunvibe.auth.core.application.usecase.user.impl;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.RefreshToken;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.NoDatabaseTransactions;
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
    private final NoDatabaseTransactions transactions = new NoDatabaseTransactions();
    private static final VerificationPolicy POLICY =
            new VerificationPolicy(Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5, Duration.ofDays(7));

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        refreshTokens = new InMemoryRefreshTokenRepository();
        audit = new RecordingAuditLog();
        service = serviceIn(AuthTestUsers.phoneOn());
    }

    private PendingAccountCleanupService serviceIn(PhoneChannelPolicy channel) {
        return new PendingAccountCleanupService(users, users, refreshTokens, POLICY, audit, channel,
                new TransactionTemplate(transactions));
    }

    private static void verifiedMailbox(AppUser user) {
        user.setEmail(Email.create("a@example.com"));
        user.setEmailKey("a@example.com");
        user.setEmailVerifiedAt(Instant.now());
    }

    /** auth-email-access 4.4: with the channel closed a PENDING account cannot verify, so it is not its fault. */
    @Test
    void cleanup_off_deletesNothing() {
        AppUser expired = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA1", "0911111111", Duration.ofDays(8));

        assertEquals(0, serviceIn(new PhoneChannelPolicy(PhoneChannelPolicy.Mode.OFF, null, audit)).execute());

        assertEquals(UserStatus.PENDING, expired.getUserStatus());
    }

    @Test
    void cleanup_legacy_deletesNothing() {
        AppUser expired = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA1", "0911111111", Duration.ofDays(8));
        PhoneChannelPolicy legacy = new PhoneChannelPolicy(PhoneChannelPolicy.Mode.LEGACY_LOGIN,
                Instant.now().plus(Duration.ofDays(1)), audit);

        assertEquals(0, serviceIn(legacy).execute());

        assertEquals(UserStatus.PENDING, expired.getUserStatus());
    }

    @Test
    void cleanup_on_onlyBothUnverified() {
        AppUser neither = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA1", "0911111111", Duration.ofDays(8));
        AppUser mailbox = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA2", "0922222222", Duration.ofDays(8));
        verifiedMailbox(mailbox);

        assertEquals(1, service.execute());

        assertEquals(UserStatus.DELETED, neither.getUserStatus());
        assertEquals(UserStatus.PENDING, mailbox.getUserStatus());
    }

    @Test
    void cleanup_emailOnlyActive_untouched() {
        AppUser emailOnly = AppUser.registerWithEmail(UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FA1"), "Nguyen Van A",
                Email.create("a@example.com"), Password.create("hashed"), new HashSet<>(Set.of("TENANT")),
                Instant.now().minus(Duration.ofDays(30)));
        users.with(emailOnly);

        assertEquals(0, service.execute());

        assertEquals(UserStatus.ACTIVE, emailOnly.getUserStatus());
    }

    /** The list is read before the lock: a mailbox or a phone proven since then saves the row. */
    @Test
    void cleanup_rowVerifiedMeanwhile_skipped() {
        AppUser byMail = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA1", "0911111111", Duration.ofDays(8));
        AppUser byPhone = registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA2", "0922222222", Duration.ofDays(8));
        verifiedMailbox(byMail);
        byPhone.setPhoneVerifiedAt(Instant.now());
        PendingAccountCleanupService staleList = new PendingAccountCleanupService(
                (cutoff, limit) -> List.of(byMail.getId(), byPhone.getId()), users, refreshTokens, POLICY, audit,
                AuthTestUsers.phoneOn(), new TransactionTemplate(transactions));

        assertEquals(0, staleList.execute());

        assertEquals(UserStatus.PENDING, byMail.getUserStatus());
        assertEquals(UserStatus.PENDING, byPhone.getUserStatus());
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    /** spec 5.4: a row lock lasts one row, so a long run blocks nobody. */
    @Test
    void cleanup_eachRowItsOwnTransaction() {
        registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA1", "0911111111", Duration.ofDays(8));
        registeredAgo("01ARZ3NDEKTSV4RRFFQ69G5FA2", "0922222222", Duration.ofDays(9));

        assertEquals(2, service.execute());

        assertEquals(2, transactions.begun);
    }

    private AppUser registeredAgo(String id, String phone, Duration age) {
        AppUser user = AuthTestUsers.withPhone(UserId.create(id), "Nguyen Van A", phone, null,
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
        assertEquals("091****111", audit.last().subject());
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
