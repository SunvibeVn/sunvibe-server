package vn.sunvibe.auth.core.application.usecase.role.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The owner named on property create (auth-roles brief BR-7, BR-8; W6-7, W6-8). */
class OwnerAccountServiceTest {

    private static final String OWNER_ID = "01OWNER0000000000000000000";
    private static final String CREATOR_ID = "01CREATOR00000000000000000";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final OwnerAccountService service = new OwnerAccountService(users, audit);

    private AppUser store(UserStatus status, RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        AppUser user = AppUser.create(UserId.create(OWNER_ID), "Owner", Username.create("0900000009"), null,
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
}
