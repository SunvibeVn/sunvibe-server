package vn.sunvibe.auth.core.application.usecase.role.impl;

import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;
import vn.sunvibe.auth.core.application.exception.role.RoleChangeForbiddenException;
import vn.sunvibe.auth.core.application.exception.role.RoleNotGrantableException;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * Checks shared by grant and revoke (auth-roles BR-9, E-4..E-7), in this order: the role is grantable (400),
 * the actor may change it (403), the target is not the actor (403), the target exists (404), and only SUPER_ADMIN
 * changes an account that holds ADMIN or SUPER_ADMIN (403; owner clarification Q-8, 2026-10-16).
 */
final class RoleChangeRules {

    /** Never contains the id: it would echo client input. */
    static final String USER_NOT_FOUND = "User not found.";

    private RoleChangeRules() {
    }

    static RoleName checkedRole(RoleChangeCommand command) {
        RoleName role = RoleName.parse(command.role())
                .filter(RoleName.GRANTABLE_BY_SUPER_ADMIN::contains)
                .orElseThrow(RoleNotGrantableException::new);
        if (!command.actorIsSuperAdmin() && !RoleName.GRANTABLE_BY_ADMIN.contains(role)) {
            throw new RoleChangeForbiddenException("Only SUPER_ADMIN may change this role.");
        }
        if (command.actorUserId() == null || command.actorUserId().equals(command.targetUserId())) {
            throw new RoleChangeForbiddenException("Nobody may change their own roles.");
        }
        return role;
    }

    /** Q-8: an ADMIN cannot change (and so cannot end the sessions of) another ADMIN or a SUPER_ADMIN. */
    static void checkAdminAccount(RoleChangeCommand command, AppUser target) {
        boolean adminAccount = target.getUserRoles().contains(RoleName.ADMIN.name())
                || target.getUserRoles().contains(RoleName.SUPER_ADMIN.name());
        if (adminAccount && !command.actorIsSuperAdmin()) {
            throw new RoleChangeForbiddenException("Only SUPER_ADMIN may change an admin account.");
        }
    }

    static AppUser target(UserDomainRepository users, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UserNotFoundException(USER_NOT_FOUND);
        }
        return users.findById(UserId.from(userId)).orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
    }

    /** Ids and the role name only, no PII (auth BR-8). */
    static AuthAuditEvent auditEvent(AuditEventType type, AppUser target, RoleName role, String actorUserId) {
        return AuthAuditEvent.of(type, target.getId().getValue(), null,
                "role=" + role.name() + ";actorUserId=" + actorUserId);
    }
}
