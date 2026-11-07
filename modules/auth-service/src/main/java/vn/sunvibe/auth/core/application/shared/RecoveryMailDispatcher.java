package vn.sunvibe.auth.core.application.shared;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.shared.external.audit.AuthAuditEvent;
import vn.sunvibe.auth.core.application.shared.external.email.AfterCommitMailPort;
import vn.sunvibe.auth.core.application.shared.external.email.MailSendGatePort;

/**
 * Recovery mail, after the commit and off the request thread (auth-email-access spec 5.6). The answer left already and is
 * neutral, so a full queue, a refusal of the gate or a failed send is only the warn line of its port and an audit event
 * without the address. The code and the token live in the task alone, never in storage.
 */
@Component
@RequiredArgsConstructor
public class RecoveryMailDispatcher {

    static final String SEND_FAILED = "EMAIL SEND_FAILED";

    private final AfterCommitMailPort mailPool;
    private final MailSendGatePort mailGate;
    private final AuditLogPort auditLog;

    /**
     * @param auditType the event a failure is recorded as
     * @param send      the mail; a failure is {@link SendingMailFailedException}
     */
    public void dispatch(AuditEventType auditType, String userId, Runnable send) {
        Runnable failed = () -> auditLog.record(AuthAuditEvent.of(auditType, userId, null, SEND_FAILED));
        mailPool.dispatch(() -> deliver(send, failed), failed);
    }

    private void deliver(Runnable send, Runnable failed) {
        if (!mailGate.tryEnter()) {
            failed.run();
            return;
        }
        try {
            if (!mailGate.tryCharge()) {
                failed.run();
                return;
            }
            send.run();
        } catch (SendingMailFailedException e) {
            failed.run();
        } finally {
            mailGate.leave();
        }
    }
}
