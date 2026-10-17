package vn.sunvibe.auth.core.application.usecase.role.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.command.role.RoleChangeCommand;
import vn.sunvibe.auth.core.application.exception.role.InactiveAccountException;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.role.RoleGrantUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

/**
 * Grants a role (auth-roles US-4, BR-9, BR-10). Only an account that may sign in gets a role (422 otherwise).
 * Granting a role already held changes nothing and is not audited (auth-roles US-4 AC-3). The audit row is written only
 * once the grant commits.
 */
@Service
@RequiredArgsConstructor
public class RoleGrantService implements RoleGrantUseCase {

    private final UserDomainRepository userRepository;
    private final AuditLogPort auditLog;

    @Transactional
    @Override
    public void execute(RoleChangeCommand command) {
        RoleName role = RoleChangeRules.checkedRole(command);
        AppUser target = RoleChangeRules.target(userRepository, command.targetUserId());
        RoleChangeRules.checkAdminAccount(command, target);
        if (!target.canAuthenticate()) {
            throw new InactiveAccountException();
        }
        if (userRepository.addRole(target.getId(), role)) {
            auditLog.recordAfterCommit(RoleChangeRules.auditEvent(AuditEventType.ROLE_GRANTED, target, role,
                    command.actorUserId()));
        }
    }
}
