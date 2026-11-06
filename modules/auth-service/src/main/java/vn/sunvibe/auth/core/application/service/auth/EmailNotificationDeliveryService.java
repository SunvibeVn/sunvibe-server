package vn.sunvibe.auth.core.application.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.usecase.auth.EmailNotificationDeliveryUseCase;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.usecase.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.usecase.external.email.EmailServicePort;
import vn.sunvibe.auth.core.application.usecase.external.email.MailSendGatePort;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.domain.repository.auth.EmailNotificationRepository;
import vn.sunvibe.shared.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A claimed notice moves {@value #CLAIM_MINUTES} minutes ahead and the claim commits before the first send, so no
 * connection waits on SMTP and a worker that dies leaves its rows for a later run. A refusal of the mail gate is no
 * failed attempt: the claim alone brings the notice back.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class EmailNotificationDeliveryService implements EmailNotificationDeliveryUseCase {

    static final int BATCH_SIZE = 20;
    private static final int CLAIM_MINUTES = 5;

    private final EmailNotificationRepository notifications;
    private final EmailServicePort emailService;
    private final MailSendGatePort mailGate;
    private final AuditLogPort auditLog;
    private final TransactionTemplate transaction;

    @Override
    public void deliverDue() {
        Instant now = AppClock.currentUTC();
        List<EmailNotification> claimed = transaction.execute(status ->
                notifications.claimDue(now, BATCH_SIZE, now.plus(Duration.ofMinutes(CLAIM_MINUTES))));
        if (claimed != null) {
            claimed.forEach(this::deliver);
        }
    }

    private void deliver(EmailNotification notification) {
        if (!mailGate.tryEnter()) {
            return;
        }
        try {
            if (!mailGate.tryCharge()) {
                return;
            }
            send(notification);
            notifications.delete(notification.getId());
        } catch (SendingMailFailedException e) {
            registerFailure(notification);
        } finally {
            mailGate.leave();
        }
    }

    private void send(EmailNotification notification) {
        if (EmailNotification.KIND_EMAIL_CHANGED.equals(notification.getKind())) {
            emailService.sendEmailChangedNotice(notification.getDestination());
        } else {
            emailService.sendOwnerNotice(notification.getDestination(), notification.getKind(),
                    notification.getCreatedAt());
        }
    }

    private void registerFailure(EmailNotification notification) {
        if (notification.registerFailure(AppClock.currentUTC())) {
            notifications.save(notification);
            return;
        }
        log.warn("auth.mail.notice-given-up user={}", notification.getUserId());
        auditLog.record(AuthAuditEvent.of(AuditEventType.EMAIL_NOTICE_FAILED, notification.getUserId(), null,
                "attempts=" + notification.getAttempts()));
        notifications.delete(notification.getId());
    }
}
