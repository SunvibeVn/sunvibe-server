package vn.sunvibe.auth.core.application.usecase.role.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;

import java.util.Optional;

/**
 * The owner named on property create (auth-roles BR-7, BR-8): eligible if the account may sign in and holds no
 * staff/admin role; gets OWNER if missing, in the caller's transaction, audited with the creator as actor.
 */
@Service
@RequiredArgsConstructor
public class OwnerAccountService implements OwnerAccountUseCase {

    private final UserDomainRepository userRepository;
    private final AuditLogPort auditLog;

    @Transactional(readOnly = true)
    @Override
    public boolean isEligibleOwner(String userId) {
        return eligibleOwner(userId).isPresent();
    }

    @Transactional
    @Override
    public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
        Optional<AppUser> owner = eligibleOwner(ownerUserId);
        if (owner.isEmpty()) {
            return false;
        }
        if (userRepository.addRole(owner.get().getId(), RoleName.OWNER)) {
            auditLog.recordAfterCommit(
                    RoleChangeRules.auditEvent(AuditEventType.ROLE_GRANTED, owner.get(), RoleName.OWNER, actorUserId));
        }
        return true;
    }

    private Optional<AppUser> eligibleOwner(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        // findById skips soft-deleted accounts
        return userRepository.findById(UserId.from(userId))
                .filter(AppUser::canAuthenticate)
                .filter(user -> !user.isStaff());
    }
}
