package vn.sunvibe.auth.core.application.service.role;

import vn.sunvibe.auth.core.testsupport.AuthTestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountContext;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;
import vn.sunvibe.auth.core.testsupport.InMemoryEmailNotificationRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The owner named on property create (auth-roles brief BR-7, BR-8; W6-7, W6-8). */
class OwnerAccountServiceTest {

    private static final String OWNER_ID = "01OWNER0000000000000000000";
    private static final String CREATOR_ID = "01CREATOR00000000000000000";

    private static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final InMemoryEmailNotificationRepository notifications = new InMemoryEmailNotificationRepository();
    private final AtomicLong ids = new AtomicLong();
    private final OwnerAccountService service = new OwnerAccountService(users, audit, notifications, ids::incrementAndGet);

    private AppUser store(UserStatus status, RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        AppUser user = AuthTestUsers.withPhone(UserId.create(OWNER_ID), "Owner", "0900000009", null,
                Password.create("hashed"), ProfileImage.create(null), null, names, Instant.parse("2026-01-01T00:00:00Z"));
        user.setUserStatus(status);
        users.with(user);
        return user;
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "PENDING"})
    void isEligibleOwner_customerWhoMaySignIn_isTrue(UserStatus status) {
        store(status, RoleName.CUSTOMER);

        assertTrue(service.isEligibleOwner(OWNER_ID));
    }

    @Test
    void isEligibleOwner_unknownBlankOrNullId_isFalse() {
        assertFalse(service.isEligibleOwner(OWNER_ID));
        assertFalse(service.isEligibleOwner(" "));
        assertFalse(service.isEligibleOwner(null));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED"})
    void isEligibleOwner_accountThatMayNotSignIn_isFalse(UserStatus status) {
        store(status, RoleName.CUSTOMER);

        assertFalse(service.isEligibleOwner(OWNER_ID));
    }

    /** auth-roles BR-7: staff/admin can never be the owner. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"SALE", "SALE_LEADER", "SALES_MANAGER", "LISTING_REVIEWER", "ADMIN",
            "SUPER_ADMIN", "EMPLOYEE", "MANAGER", "LEADER"})
    void isEligibleOwner_staffOrAdmin_isFalse(RoleName staffRole) {
        store(UserStatus.ACTIVE, RoleName.CUSTOMER, staffRole);

        assertFalse(service.isEligibleOwner(OWNER_ID));
    }

    /** auth-roles BR-8: OWNER is granted if missing, audited with the creator as actor. */
    @Test
    void ensureOwnerRole_missing_grantsAndAuditsWithTheCreator() {
        AppUser owner = store(UserStatus.ACTIVE, RoleName.CUSTOMER);

        assertTrue(service.ensureOwnerRole(OWNER_ID, CREATOR_ID));

        assertEquals(Set.of("CUSTOMER", "OWNER"), owner.getUserRoles());
        assertEquals(List.of(AuditEventType.ROLE_GRANTED), audit.types());
        assertEquals(OWNER_ID, audit.last().userId());
        assertEquals("role=OWNER;actorUserId=" + CREATOR_ID, audit.last().details());
    }

    @Test
    void ensureOwnerRole_alreadyOwner_changesNothing() {
        store(UserStatus.ACTIVE, RoleName.OWNER);
        int savesBefore = users.saveCount;

        assertTrue(service.ensureOwnerRole(OWNER_ID, CREATOR_ID));

        assertEquals(savesBefore, users.saveCount);
        assertTrue(audit.events.isEmpty());
    }

    /** The check and the grant are separate calls: the grant re-checks and grants nothing to an ineligible owner. */
    @Test
    void ensureOwnerRole_ownerNoLongerEligible_grantsNothingAndSaysSo() {
        AppUser owner = store(UserStatus.ACTIVE, RoleName.SALE);

        assertFalse(service.ensureOwnerRole(OWNER_ID, CREATOR_ID));
        assertEquals(Set.of("SALE"), owner.getUserRoles());
        assertTrue(audit.events.isEmpty());
    }

    /** property-create intake spec 5.1: the grant holds the account row, so a lock cannot slip past the check. */
    @Test
    void ensureOwnerRole_readsUnderTheRowLock() {
        store(UserStatus.ACTIVE, RoleName.CUSTOMER);

        service.ensureOwnerRole(OWNER_ID, CREATOR_ID);

        assertEquals(List.of("lock-user"), users.calls);
    }

    // ==================== owner routing (property-create intake D5-5, S-4) ====================

    private AppUser storeWithEmail(String address, RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        AppUser user = AppUser.registerWithEmail(UserId.create(OWNER_ID), "Owner", Email.create(address),
                Password.create("hashed"), names, NOW);
        users.with(user);
        return user;
    }

    @Test
    void lookup_found_verifiedSignInNonStaff() {
        storeWithEmail("owner@example.com", RoleName.CUSTOMER);

        OwnerLookup found = service.lookupConfirmable("  Owner@Example.com ");

        assertEquals(OwnerLookup.Outcome.FOUND, found.outcome());
        assertEquals(OWNER_ID, found.userIdOrNull());
    }

    /** D5-5: staff, an unverified address and a phone-only account answer exactly as an unknown address does. */
    @Test
    void lookup_staff_unverified_phoneOnly_notFound() {
        storeWithEmail("staff@example.com", RoleName.SALE);
        assertEquals(OwnerLookup.notFound(), service.lookupConfirmable("staff@example.com"));

        OwnerAccountServiceTest other = new OwnerAccountServiceTest();
        AppUser unverified = other.store(UserStatus.ACTIVE, RoleName.CUSTOMER);
        unverified.setEmail(Email.create("unverified@example.com"));
        assertEquals(OwnerLookup.notFound(), other.service.lookupConfirmable("unverified@example.com"));

        OwnerAccountServiceTest phoneOnly = new OwnerAccountServiceTest();
        phoneOnly.store(UserStatus.ACTIVE, RoleName.CUSTOMER);
        assertEquals(OwnerLookup.notFound(), phoneOnly.service.lookupConfirmable("nobody@example.com"));

        OwnerAccountServiceTest locked = new OwnerAccountServiceTest();
        locked.storeWithEmail("locked@example.com", RoleName.CUSTOMER).setUserStatus(UserStatus.LOCK);
        assertEquals(OwnerLookup.notFound(), locked.service.lookupConfirmable("locked@example.com"));
    }

    /** spec 4.6 step 1: the email policy runs first, so a malformed value never reaches a query. */
    @Test
    void lookup_malformed_invalidBeforeAnyRead() {
        CountingUsers counting = new CountingUsers();
        OwnerAccountService counted = new OwnerAccountService(counting, audit, notifications, () -> 1L);

        assertEquals(OwnerLookup.invalid(), counted.lookupConfirmable("not an email"));
        assertEquals(OwnerLookup.invalid(), counted.lookupConfirmable(null));
        assertEquals(0, counting.emailReads);
    }

    /** S-4: found and unknown take the same path, one read each, so timing and counters show no difference. */
    @Test
    void lookup_samePathForFoundAndNotFound() {
        CountingUsers counting = new CountingUsers();
        AppUser user = AppUser.registerWithEmail(UserId.create(OWNER_ID), "Owner", Email.create("owner@example.com"),
                Password.create("hashed"), new HashSet<>(Set.of("CUSTOMER")), NOW);
        counting.with(user);
        OwnerAccountService counted = new OwnerAccountService(counting, audit, notifications, () -> 1L);

        counted.lookupConfirmable("owner@example.com");
        int afterFound = counting.emailReads;
        counted.lookupConfirmable("nobody@example.com");

        assertEquals(1, afterFound);
        assertEquals(2, counting.emailReads);
    }

    @Test
    void lookup_toString_hidesTheUser() {
        assertFalse(OwnerLookup.found(OWNER_ID).toString().contains(OWNER_ID));
    }

    /** spec 4.6 step 6: the manager sees a masked address and the account's age, never the address itself. */
    @Test
    void context_masked() {
        storeWithEmail("owner@example.com", RoleName.CUSTOMER);
        users.standing(OWNER_ID, new AccountStanding(UserStatus.ACTIVE.name(), null, 0, NOW.minus(Duration.ofDays(40))));

        OwnerAccountContext context = service.accountContext(OWNER_ID);

        assertEquals("o***@example.com", context.addressMasked());
        assertTrue(context.ageDays() >= 30);
    }

    @Test
    void context_noAddress_masksToStars() {
        store(UserStatus.ACTIVE, RoleName.CUSTOMER);
        users.standing(OWNER_ID, new AccountStanding(UserStatus.ACTIVE.name(), null, 0, NOW));

        assertEquals("***", service.accountContext(OWNER_ID).addressMasked());
    }

    // ==================== notices (property-create intake A-12) ====================

    @ParameterizedTest
    @EnumSource(OwnerNoticeKind.class)
    void notify_writesOneNoticeToTheVerifiedAddress(OwnerNoticeKind kind) {
        storeWithEmail("owner@example.com", RoleName.CUSTOMER);

        service.notifyOwner(OWNER_ID, kind);

        EmailNotification notice = notifications.claimDue(Instant.now().plusSeconds(60), 10, Instant.now()).getFirst();
        assertEquals(kind.name(), notice.getKind());
        assertEquals("owner@example.com", notice.getDestination());
        assertEquals(OWNER_ID, notice.getUserId());
    }

    /** An account with no verified address has nowhere to send a notice; the command still goes on. */
    @Test
    void notify_noVerifiedAddress_writesNothing() {
        store(UserStatus.ACTIVE, RoleName.CUSTOMER);

        service.notifyOwner(OWNER_ID, OwnerNoticeKind.OWNER_CONFIRMED);

        assertTrue(notifications.claimDue(Instant.now().plusSeconds(60), 10, Instant.now()).isEmpty());
    }

    // ==================== standing (property-create intake D5-10) ====================

    @Test
    void standing_staffRole_notEligible_keepsCount() {
        store(UserStatus.ACTIVE, RoleName.SALE);
        users.standing(OWNER_ID, new AccountStanding(UserStatus.ACTIVE.name(), null, 2, NOW));
        users.roleNames(OWNER_ID, Set.of("SALE"));

        assertEquals(new OwnerStanding(false, 2), service.ownerStanding(OWNER_ID));
    }

    @Test
    void standing_unknownAccount_notEligible() {
        assertEquals(new OwnerStanding(false, -1), service.ownerStanding(OWNER_ID));
    }

    /** Users whose email reads are counted. */
    private static final class CountingUsers extends InMemoryUserRepository {
        int emailReads;

        @Override
        public Optional<AppUser> findByEmailKey(String emailKey) {
            emailReads++;
            return super.findByEmailKey(emailKey);
        }
    }
}
