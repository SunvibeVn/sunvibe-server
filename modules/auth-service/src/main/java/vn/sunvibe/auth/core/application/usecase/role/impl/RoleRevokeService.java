package vn.sunvibe.auth.core.application.usecase.role.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.role.RoleRevokeUseCase;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.time.AppClock;

/**
 * Revokes a role and ends every session of the account (auth-roles US-4 AC-5, BR-11): its refresh tokens are
 * revoked, and its access tokens lose the role on the next request because roles are reloaded each time (auth BR-3).
 * Revoking a role that is not held changes nothing and keeps the sessions (US-4 AC-3).
 */
@Service
@RequiredArgsConstructor
public class RoleRevokeService implements RoleRevokeUseCase {

    private final UserDomainRepository userRepository;
    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final AuditLogPort auditLog;

    @Transactional
    @Override
    public void execute(RoleChangeCommand command) {
        RoleName role = RoleChangeRules.checkedRole(command);
        AppUser target = RoleChangeRules.target(userRepository, command.targetUserId());
        RoleChangeRules.checkAdminAccount(command, target);
        if (userRepository.removeRole(target.getId(), role)) {
            refreshTokenRepository.revokeAllForUser(target.getId(), RevokeReason.ROLE_REVOKED, AppClock.currentUTC());
            auditLog.recordAfterCommit(RoleChangeRules.auditEvent(AuditEventType.ROLE_REVOKED, target, role,
                    command.actorUserId()));
        }
    }
}
