package vn.sunvibe.auth.core.application.usecase.role.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;
import vn.sunvibe.auth.core.application.exception.role.InactiveAccountException;
import vn.sunvibe.auth.core.application.exception.role.RoleChangeForbiddenException;
import vn.sunvibe.auth.core.application.exception.role.RoleNotGrantableException;
import vn.sunvibe.auth.core.application.query.role.UserRolesResult;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.testsupport.InMemoryRefreshTokenRepository;
import vn.sunvibe.auth.core.testsupport.InMemoryUserRepository;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Role admin rules (auth-roles brief US-4, BR-9..BR-11, E-4..E-7; owner decision W6-9).
 * Q-5 = 403 for a self change, Q-6 = revoking OWNER is allowed, Q-7 = an account may end with no roles.
 */
class RoleChangeServiceTest {

    private static final String ADMIN_ID = "01ADMIN0000000000000000000";
    private static final String SUPER_ADMIN_ID = "01SUPERADMIN00000000000000";
    private static final String TARGET_ID = "01TARGET000000000000000000";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
    private final RecordingAuditLog audit = new RecordingAuditLog();

    private final RoleGrantService grant = new RoleGrantService(users, audit);
    private final RoleRevokeService revoke = new RoleRevokeService(users, refreshTokens, audit);
    private final UserRoleQueryService query = new UserRoleQueryService(users);

    private AppUser target;

    RoleChangeServiceTest() {
        users.with(user(ADMIN_ID, "0900000001", UserStatus.ACTIVE, RoleName.ADMIN));
        users.with(user(SUPER_ADMIN_ID, "0900000002", UserStatus.ACTIVE, RoleName.SUPER_ADMIN));
        target = user(TARGET_ID, "0900000003", UserStatus.ACTIVE, RoleName.CUSTOMER);
        users.with(target);
    }

    private static AppUser user(String id, String username, UserStatus status, RoleName... roles) {
        Set<String> names = new HashSet<>();
        for (RoleName role : roles) {
            names.add(role.name());
        }
        AppUser user = AppUser.create(UserId.create(id), "User", Username.create(username), null,
                Password.create("hashed"), ProfileImage.create(null), null, names, Instant.parse("2026-01-01T00:00:00Z"));
        user.setUserStatus(status);
        return user;
    }

    private static RoleChangeCommand byAdmin(String role) {
        return new RoleChangeCommand(ADMIN_ID, false, TARGET_ID, role);
    }

    private static RoleChangeCommand bySuperAdmin(String role) {
        return new RoleChangeCommand(SUPER_ADMIN_ID, true, TARGET_ID, role);
    }

    // ==================== grant ====================

    /** auth-roles US-4 AC-1: ADMIN grants the six new roles. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"OWNER", "CUSTOMER", "SALE", "SALE_LEADER", "SALES_MANAGER",
            "LISTING_REVIEWER"})
    void grant_adminGrantsANewRole_addsItAndAudits(RoleName role) {
        target.revokeRole(RoleName.CUSTOMER);

        grant.execute(byAdmin(role.name()));

        assertEquals(Set.of(role.name()), target.getUserRoles());
        assertEquals(List.of(AuditEventType.ROLE_GRANTED), audit.types());
        assertEquals(TARGET_ID, audit.last().userId());
        assertEquals("role=" + role.name() + ";actorUserId=" + ADMIN_ID, audit.last().details());
        assertNull(audit.last().subject(), "no PII beyond ids");
    }

    /** auth-roles US-4 AC-2, E-4: only SUPER_ADMIN grants ADMIN. */
    @Test
    void grant_adminGrantsAdmin_isForbidden() {
        assertThrows(RoleChangeForbiddenException.class, () -> grant.execute(byAdmin("ADMIN")));
        assertFalse(target.getUserRoles().contains("ADMIN"));
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void grant_superAdminGrantsAdmin_addsIt() {
        grant.execute(bySuperAdmin("ADMIN"));

        assertTrue(target.getUserRoles().contains("ADMIN"));
        assertEquals(List.of(AuditEventType.ROLE_GRANTED), audit.types());
    }

    /** auth-roles E-6: SUPER_ADMIN, every old role and unknown names are not grantable, even by SUPER_ADMIN. */
    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "LANDLORD", "TENANT", "EMPLOYEE", "MANAGER", "LEADER", "USER", "KING", "sale", ""})
    void grant_roleNotGrantable_isBadRequest(String role) {
        assertThrows(RoleNotGrantableException.class, () -> grant.execute(bySuperAdmin(role)));
        assertEquals(Set.of("CUSTOMER"), target.getUserRoles());
    }

    /** auth-roles BR-9, E-5, Q-5: no self grant, not even SUPER_ADMIN. */
    @Test
    void grant_toSelf_isForbidden() {
        assertThrows(RoleChangeForbiddenException.class,
                () -> grant.execute(new RoleChangeCommand(SUPER_ADMIN_ID, true, SUPER_ADMIN_ID, "SALE")));
    }

    /** auth-roles E-7: unknown target -> 404. */
    @Test
    void grant_unknownUser_isNotFound() {
        assertThrows(UserNotFoundException.class,
                () -> grant.execute(new RoleChangeCommand(ADMIN_ID, false, "01NOSUCHUSER00000000000000", "SALE")));
    }

    /** auth-roles BR-10, E-7: grant only to an account that may sign in. */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCK", "TERMINATED"})
    void grant_inactiveUser_isUnprocessable(UserStatus status) {
        target.setUserStatus(status);

        assertThrows(InactiveAccountException.class, () -> grant.execute(byAdmin("SALE")));
        assertEquals(Set.of("CUSTOMER"), target.getUserRoles());
        assertTrue(audit.events.isEmpty());
    }

    /** auth-roles US-4 AC-3: granting a role already held changes nothing and is not audited. */
    @Test
    void grant_roleAlreadyHeld_isANoOp() {
        int savesBefore = users.saveCount;

        grant.execute(byAdmin("CUSTOMER"));

        assertEquals(savesBefore, users.saveCount);
        assertTrue(audit.events.isEmpty());
    }

    // ==================== admin accounts (owner clarification Q-8, 2026-10-16) ====================

    /** Q-8: only SUPER_ADMIN changes any role of an account holding ADMIN or SUPER_ADMIN. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ADMIN", "SUPER_ADMIN"})
    void grant_adminChangesAnAdminAccount_isForbidden(RoleName targetRole) {
        target = user(TARGET_ID, "0900000003", UserStatus.ACTIVE, RoleName.CUSTOMER, targetRole);
        users.with(target);

        assertThrows(RoleChangeForbiddenException.class, () -> grant.execute(byAdmin("SALE")));
        assertFalse(target.getUserRoles().contains("SALE"));
        assertTrue(audit.events.isEmpty());
    }

    /** Q-8: an ADMIN cannot end a SUPER_ADMIN's (or another ADMIN's) sessions through a revoke. */
    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"ADMIN", "SUPER_ADMIN"})
    void revoke_adminChangesAnAdminAccount_isForbiddenAndKeepsSessions(RoleName targetRole) {
        target = user(TARGET_ID, "0900000003", UserStatus.ACTIVE, RoleName.CUSTOMER, targetRole);
        users.with(target);

        assertThrows(RoleChangeForbiddenException.class, () -> revoke.execute(byAdmin("CUSTOMER")));
        assertTrue(target.getUserRoles().contains("CUSTOMER"));
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void grant_superAdminChangesAnAdminAccount_isAllowed() {
        target.grantRole(RoleName.ADMIN);

        grant.execute(bySuperAdmin("LISTING_REVIEWER"));

        assertTrue(target.getUserRoles().contains("LISTING_REVIEWER"));
    }

    // ==================== check order: 400 role, 403 permission, 403 self, 404, 403 admin account, 422 ====================

    @Test
    void grant_selfWithUnknownRole_isBadRequestFirst() {
        assertThrows(RoleNotGrantableException.class,
                () -> grant.execute(new RoleChangeCommand(ADMIN_ID, false, ADMIN_ID, "KING")));
    }

    @Test
    void grant_adminChangesOwnAdmin_isForbidden() {
        assertThrows(RoleChangeForbiddenException.class,
                () -> grant.execute(new RoleChangeCommand(ADMIN_ID, false, ADMIN_ID, "ADMIN")));
    }

    /** Self is decided from the ids alone, before any lookup. */
    @Test
    void grant_selfWithUnknownOwnId_isForbiddenNotNotFound() {
        String ghost = "01GHOST0000000000000000000";

        assertThrows(RoleChangeForbiddenException.class,
                () -> grant.execute(new RoleChangeCommand(ghost, true, ghost, "SALE")));
    }

    // ==================== revoke ====================

    /** auth-roles US-4 AC-4, AC-5, BR-11: revoke removes the role, audits and ends every session of the target. */
    @Test
    void revoke_heldRole_removesItAuditsAndRevokesSessions() {
        target.grantRole(RoleName.SALE);

        revoke.execute(byAdmin("SALE"));

        assertEquals(Set.of("CUSTOMER"), target.getUserRoles());
        assertEquals(List.of(AuditEventType.ROLE_REVOKED), audit.types());
        assertEquals("role=SALE;actorUserId=" + ADMIN_ID, audit.last().details());
        assertEquals(List.of("user:" + TARGET_ID + ":ROLE_REVOKED"), refreshTokens.revocations);
    }

    /** auth-roles US-4 AC-3: revoking a role that is not held changes nothing and keeps the sessions. */
    @Test
    void revoke_roleNotHeld_isANoOp() {
        revoke.execute(byAdmin("SALE"));

        assertTrue(audit.events.isEmpty());
        assertTrue(refreshTokens.revocations.isEmpty());
    }

    @Test
    void revoke_adminRevokesAdmin_isForbidden() {
        target.grantRole(RoleName.ADMIN);

        assertThrows(RoleChangeForbiddenException.class, () -> revoke.execute(byAdmin("ADMIN")));
        assertTrue(target.getUserRoles().contains("ADMIN"));
    }

    @Test
    void revoke_superAdminRevokesAdmin_removesIt() {
        target.grantRole(RoleName.ADMIN);

        revoke.execute(bySuperAdmin("ADMIN"));

        assertFalse(target.getUserRoles().contains("ADMIN"));
    }

    @Test
    void revoke_oldRole_isBadRequest() {
        assertThrows(RoleNotGrantableException.class, () -> revoke.execute(bySuperAdmin("TENANT")));
    }

    @Test
    void revoke_fromSelf_isForbidden() {
        assertThrows(RoleChangeForbiddenException.class,
                () -> revoke.execute(new RoleChangeCommand(ADMIN_ID, false, ADMIN_ID, "SALE")));
    }

    /** Revoke is allowed on an account that may no longer sign in (only grant needs an active account). */
    @Test
    void revoke_lockedUser_removesTheRole() {
        target.grantRole(RoleName.SALE);
        target.setUserStatus(UserStatus.LOCK);

        revoke.execute(byAdmin("SALE"));

        assertFalse(target.getUserRoles().contains("SALE"));
    }

    /** Q-6 default: revoking OWNER is allowed. */
    @Test
    void revoke_owner_isAllowed() {
        target.grantRole(RoleName.OWNER);

        revoke.execute(byAdmin("OWNER"));

        assertFalse(target.getUserRoles().contains("OWNER"));
    }

    // ==================== query ====================

    @Test
    void query_knownUser_returnsSortedRoles() {
        target.grantRole(RoleName.SALE);

        UserRolesResult result = query.execute(TARGET_ID);

        assertEquals(TARGET_ID, result.userId());
        assertEquals(List.of("CUSTOMER", "SALE"), result.roles());
    }

    @Test
    void query_unknownUser_isNotFound() {
        assertThrows(UserNotFoundException.class, () -> query.execute("01NOSUCHUSER00000000000000"));
    }
}
