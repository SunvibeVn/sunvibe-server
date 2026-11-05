package vn.sunvibe.auth.core.application.usecase.role.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountContext;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.EmailNotificationRepository;
import vn.sunvibe.auth.core.domain.repository.user.AccountStanding;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * The owner named on property create (auth-roles BR-7, BR-8): eligible if the account may sign in and holds no
 * staff/admin role; gets OWNER if missing, in the caller's transaction, audited with the creator as actor. It also answers
 * the owner confirmation of the inventory intake (property-create intake D5-5, D5-10, A-12).
 */
@Service
@RequiredArgsConstructor
public class OwnerAccountService implements OwnerAccountUseCase {

    /** The standing of an id with no row: no proof can have stored it. */
    private static final int NO_ROW = -1;

    private final UserDomainRepository userRepository;
    private final AuditLogPort auditLog;
    private final EmailNotificationRepository notifications;
    private final IdGenerator<Long> snowflakeIdGenerator;

    @Transactional(readOnly = true)
    @Override
    public boolean isEligibleOwner(String userId) {
        return eligibleOwner(userId).isPresent();
    }

    /** Under the row lock: an administrator's lock cannot pass between the check and the grant. */
    @Transactional
    @Override
    public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            return false;
        }
        Optional<AppUser> owner = userRepository.findByIdForSecurity(UserId.from(ownerUserId)).map(LockedUser::user)
                .filter(OwnerAccountService::mayOwn);
        if (owner.isEmpty()) {
            return false;
        }
        if (userRepository.addRole(owner.get().getId(), RoleName.OWNER)) {
            auditLog.recordAfterCommit(
                    RoleChangeRules.auditEvent(AuditEventType.ROLE_GRANTED, owner.get(), RoleName.OWNER, actorUserId));
        }
        return true;
    }

    /** The policy first, so a malformed value reaches no query; then one read, on the same path for every value. */
    @Transactional(readOnly = true)
    @Override
    public OwnerLookup lookupConfirmable(String email) {
        Email address;
        try {
            address = Email.create(email);
        } catch (AuthValidationException e) {
            return OwnerLookup.invalid();
        }
        return userRepository.findByEmailKey(address.getValue())
                .filter(AppUser::isEmailVerified)
                .filter(OwnerAccountService::mayOwn)
                .map(user -> OwnerLookup.found(user.getId().getValue()))
                .orElseGet(OwnerLookup::notFound);
    }

    @Transactional(readOnly = true)
    @Override
    public OwnerAccountContext accountContext(String userId) {
        UserId id = UserId.from(userId);
        String masked = userRepository.findById(id).map(AppUser::getEmail)
                .map(email -> ContactMasker.maskEmail(email.getValue()))
                .orElseGet(() -> ContactMasker.maskEmail(null));
        Instant created = userRepository.findStanding(id).map(AccountStanding::createdDate).orElse(null);
        long age = created == null ? 0 : Duration.between(created, AppClock.currentUTC()).toDays();
        return new OwnerAccountContext(masked, age);
    }

    /** Scalar reads past the persistence context: a password check can leave a stale entity there (spec 5.1). */
    @Transactional(readOnly = true)
    @Override
    public OwnerStanding ownerStanding(String userId) {
        UserId id = UserId.from(userId);
        Optional<AccountStanding> standing = userRepository.findStanding(id);
        if (standing.isEmpty()) {
            return new OwnerStanding(false, NO_ROW);
        }
        AccountStanding row = standing.get();
        boolean mayAuthenticate = row.deletedAt() == null
                && !AppUser.SIGN_IN_BLOCKED_STATUSES.contains(UserStatus.valueOf(row.userStatus()));
        return new OwnerStanding(mayAuthenticate && !holdsStaffRole(userRepository.findRoleNames(id)),
                row.signInLosses());
    }

    /** No verified address means nowhere to write to; the command it serves goes on all the same. */
    @Transactional
    @Override
    public void notifyOwner(String userId, OwnerNoticeKind kind) {
        userRepository.findById(UserId.from(userId))
                .filter(AppUser::isEmailVerified)
                .ifPresent(user -> notifications.save(EmailNotification.ownerNotice(snowflakeIdGenerator.generateId(),
                        userId, kind.name(), user.getEmail().getValue(), AppClock.currentUTC())));
    }

    private Optional<AppUser> eligibleOwner(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        // findById skips soft-deleted accounts
        return userRepository.findById(UserId.from(userId)).filter(OwnerAccountService::mayOwn);
    }

    private static boolean mayOwn(AppUser user) {
        return user.canAuthenticate() && !user.isStaff();
    }

    private static boolean holdsStaffRole(Set<String> roleNames) {
        return RoleName.STAFF.stream().anyMatch(role -> roleNames.contains(role.name()));
    }
}
