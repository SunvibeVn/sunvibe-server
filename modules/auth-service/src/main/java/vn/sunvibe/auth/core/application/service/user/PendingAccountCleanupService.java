package vn.sunvibe.auth.core.application.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.shared.ContactMasker;
import vn.sunvibe.auth.core.application.shared.PhoneChannelPolicy;
import vn.sunvibe.auth.core.application.shared.VerificationPolicy;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.shared.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.user.PendingAccountCleanupUseCase;
import vn.sunvibe.auth.core.domain.model.auth.enums.RevokeReason;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.UserId;
import vn.sunvibe.auth.core.domain.repository.auth.RefreshTokenDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.PendingAccountRepository;
import vn.sunvibe.auth.core.domain.repository.user.LockedUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Deactivates accounts that proved neither a phone nor a mailbox within
 * {@link VerificationPolicy#getPendingAccountTtl()} (7 days) of registration. Only with the phone channel
 * {@code on}: closed, nobody could verify, so an old PENDING row is no sign of a wrong number (auth-email-access 4.4).
 * <p>
 * Soft delete, not hard delete: the row and its audit trail stay, while the
 * phone number (and e-mail) become free again, so a number registered by
 * someone who could not verify it returns to its real owner. Sessions of the
 * account are revoked. Each row has its own transaction, so a row lock lasts one row.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PendingAccountCleanupService implements PendingAccountCleanupUseCase {

    /** Accounts handled per run; the rest are picked up by the next run. */
    static final int BATCH_SIZE = 500;

    private final PendingAccountRepository pendingAccountRepository;
    private final UserDomainRepository userRepository;
    private final RefreshTokenDomainRepository refreshTokenRepository;
    private final VerificationPolicy verificationPolicy;
    private final AuditLogPort auditLog;
    private final PhoneChannelPolicy phoneChannel;
    private final TransactionTemplate transaction;

    @Override
    public int execute() {
        if (!phoneChannel.isOn()) {
            return 0;
        }
        Instant now = AppClock.currentUTC();
        Instant cutoff = now.minus(verificationPolicy.getPendingAccountTtl());
        List<UserId> expired = pendingAccountRepository.findUnverifiedPendingCreatedBefore(cutoff, BATCH_SIZE);

        int deactivated = 0;
        for (UserId userId : expired) {
            if (Boolean.TRUE.equals(transaction.execute(status -> deactivate(userId, now)))) {
                deactivated++;
            }
        }

        if (expired.size() == BATCH_SIZE) {
            log.warn("Pending-account cleanup hit its batch size ({}); the rest will be handled next run", BATCH_SIZE);
        }
        return deactivated;
    }

    private boolean deactivate(UserId userId, Instant now) {
        Optional<LockedUser> account = userRepository.findByIdForSecurity(userId);
        // Re-check: the user may have verified since the query ran
        if (account.isEmpty() || account.get().user().getUserStatus() != UserStatus.PENDING
                || account.get().user().isPhoneVerified() || account.get().user().getEmailVerifiedAt() != null) {
            return false;
        }
        AppUser user = account.get().user();
        String masked = ContactMasker.maskPhone(user.usernameValue());
        user.deactivate();
        userRepository.save(account.get());
        refreshTokenRepository.revokeAllForUser(userId, RevokeReason.ACCOUNT_INACTIVE, now);
        auditLog.record(AuthAuditEvent.of(AuditEventType.PENDING_ACCOUNT_EXPIRED, userId.getValue(), masked,
                "unverified for " + verificationPolicy.getPendingAccountTtl()));
        return true;
    }
}
