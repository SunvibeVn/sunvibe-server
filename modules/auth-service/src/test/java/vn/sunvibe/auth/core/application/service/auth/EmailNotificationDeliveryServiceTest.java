package vn.sunvibe.auth.core.application.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.auth.core.application.exception.auth.SendingMailFailedException;
import vn.sunvibe.auth.core.application.shared.external.audit.AuditEventType;
import vn.sunvibe.auth.core.domain.model.auth.EmailNotification;
import vn.sunvibe.auth.core.testsupport.FakeMailGate;
import vn.sunvibe.auth.core.testsupport.InMemoryEmailNotificationRepository;
import vn.sunvibe.auth.core.testsupport.NoDatabaseTransactions;
import vn.sunvibe.auth.core.testsupport.RecordingAuditLog;
import vn.sunvibe.auth.core.testsupport.RecordingEmailService;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access spec 5.6, S-25: the notice is kept until it is sent or given up, and then the address is gone. */
class EmailNotificationDeliveryServiceTest {

    private static final String OLD_ADDRESS = "old@example.com";

    /** A sender that fails while told to, as a mail server that is down. */
    private static final class FailingEmail extends RecordingEmailService {
        boolean down;

        @Override
        public void sendEmailChangedNotice(String to) {
            if (down) {
                throw new SendingMailFailedException("down");
            }
            super.sendEmailChangedNotice(to);
        }
    }

    private final InMemoryEmailNotificationRepository notifications = new InMemoryEmailNotificationRepository();
    private final FailingEmail email = new FailingEmail();
    private final FakeMailGate gate = new FakeMailGate();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final EmailNotificationDeliveryService service = new EmailNotificationDeliveryService(notifications, email, gate,
            audit, new TransactionTemplate(new NoDatabaseTransactions()));

    private void aNotice() {
        notifications.save(EmailNotification.emailChanged(1L, "user-1", OLD_ADDRESS, Instant.now().minusSeconds(1)));
    }

    /** property-create intake A-12: an owner notice goes out as its own kind, never as an email-changed notice. */
    @Test
    void notice_ownerKind_sentAsThatKind_rowDeleted() {
        notifications.save(EmailNotification.ownerNotice(2L, "user-2", "OWNER_WITHDRAWN", "owner@example.com",
                Instant.now().minusSeconds(1)));

        service.deliverDue();

        assertEquals("owner@example.com", email.ofKind("OWNER_WITHDRAWN").getFirst().to());
        assertTrue(email.ofKind("EMAIL_CHANGED").isEmpty());
        assertTrue(notifications.all().isEmpty());
    }

    @Test
    void notice_rowDeleted_afterSendAndGiveUp() {
        aNotice();

        service.deliverDue();

        assertEquals(OLD_ADDRESS, email.ofKind("EMAIL_CHANGED").getFirst().to());
        assertTrue(notifications.all().isEmpty(), "sent: the address is not kept");
        assertEquals(gate.entered, gate.left);

        aNotice();
        email.down = true;
        for (int attempt = 0; attempt < 6; attempt++) {
            service.deliverDue();
            notifications.makeDue(Instant.now().minusSeconds(1));
        }

        assertTrue(notifications.all().isEmpty(), "given up: the address is not kept");
    }

    @Test
    void notice_senderFails_bindingKept_retries_thenGivesUp() {
        aNotice();
        email.down = true;

        for (int failure = 1; failure <= 5; failure++) {
            service.deliverDue();
            assertEquals(failure, notifications.all().getFirst().getAttempts());
            assertTrue(notifications.all().getFirst().getNextAttemptAt().isAfter(Instant.now()), "waits for its retry");
            notifications.makeDue(Instant.now().minusSeconds(1));
        }

        service.deliverDue();

        assertTrue(notifications.all().isEmpty(), "given up: the row is deleted, so the address is not kept");
        assertEquals(java.util.List.of(AuditEventType.EMAIL_NOTICE_FAILED), audit.types());
    }

    @Test
    void notice_retrySchedule_isOneFiveThirtyMinutesThenHours() {
        Instant now = Instant.now();
        EmailNotification notice = EmailNotification.emailChanged(1L, "user-1", OLD_ADDRESS, now);

        for (Duration wait : java.util.List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
                Duration.ofHours(2), Duration.ofHours(6))) {
            assertTrue(notice.registerFailure(now));
            assertEquals(now.plus(wait), notice.getNextAttemptAt());
        }

        assertFalse(notice.registerFailure(now));
    }

    @Test
    void notice_gateRefused_keepsAttempt() {
        aNotice();
        gate.full = true;

        service.deliverDue();

        assertEquals(0, notifications.all().getFirst().getAttempts());
        assertTrue(email.sent.isEmpty());
        assertEquals(0, gate.left, "no place was taken");
    }

    @Test
    void notice_overBudget_keepsAttempt() {
        aNotice();
        gate.overBudget = true;

        service.deliverDue();

        assertEquals(0, notifications.all().getFirst().getAttempts());
        assertTrue(email.sent.isEmpty());
        assertEquals(gate.entered, gate.left);
    }

    @Test
    void notice_notDueYet_waits() {
        notifications.save(EmailNotification.load(1L, "user-1", EmailNotification.KIND_EMAIL_CHANGED, OLD_ADDRESS,
                Instant.now(), 1, Instant.now().plus(Duration.ofMinutes(5))));

        service.deliverDue();

        assertTrue(email.sent.isEmpty());
    }
}
