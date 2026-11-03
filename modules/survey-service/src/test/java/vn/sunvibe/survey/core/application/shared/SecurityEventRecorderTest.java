package vn.sunvibe.survey.core.application.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.logging.RequestId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.testsupport.InMemoryChecklistRepositories;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaRepositories;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security events are written after the refused request's transaction ended, in a short transaction of their own; a
 * failing write never changes the answer (survey-checklist spec 4.10, A-24, PD-6). Media refusal events are throttled
 * to one per user, resource, action and outcome a minute and carry the server request id; media action events are
 * written in the caller's transaction, never throttled (survey-media spec A-19, 4.9).
 */
class SecurityEventRecorderTest {

    private static final String USER = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";
    private static final String CHECKLIST = "01JCHECK000000000000000001";
    private static final String OTHER_CHECKLIST = "01JCHECK000000000000000002";

    private final InMemoryChecklistRepositories.SecurityEvents events = new InMemoryChecklistRepositories.SecurityEvents();
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final List<String> journal = new ArrayList<>();
    private final InMemoryMediaRepositories.Counters counters = new InMemoryMediaRepositories.Counters(journal,
            new InMemoryMediaRepositories.Media(journal));
    private Instant now = Instant.parse("2026-11-02T03:00:00Z");
    private final SecurityEventRecorder recorder = new SecurityEventRecorder(events, counters, transactions,
            new AtomicLong(1L)::incrementAndGet, () -> now);

    {
        counters.eventSource = events::all;
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        MDC.clear();
    }

    @Test
    void refused_outsideATransaction_isWrittenAtOnceWithTheCorrelationId() {
        MDC.put(CorrelationId.MDC_KEY, "corr-123");

        recorder.refused(USER, LEAD, SecurityResourceType.CHECKLIST, "01JCHECK000000000000000001",
                SecurityAction.READ, SecurityOutcome.NOT_VISIBLE);

        SurveySecurityEvent event = events.all().get(0);
        assertEquals(USER, event.userId());
        assertEquals(LEAD, event.leadId());
        assertEquals("corr-123", event.correlationIdOrNull());
        assertEquals(List.of("begin:REQUIRES_NEW", "commit"), transactions.calls);
    }

    @Test
    void refused_insideATransaction_isWrittenAfterItsRollback() {
        TransactionSynchronizationManager.initSynchronization();

        recorder.refused(USER, LEAD, SecurityResourceType.LEAD, LEAD, SecurityAction.READ,
                SecurityOutcome.NOT_VISIBLE);

        assertEquals(List.of(), events.all());
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        registered.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals(1, events.all().size());
        assertEquals(SecurityResourceType.LEAD, events.all().get(0).resourceType());
    }

    @Test
    void refused_writeFails_isSwallowedSoTheAnswerStays() {
        events.failure = new IllegalStateException("table is down");

        recorder.refused(USER, LEAD, SecurityResourceType.CHECKLIST, "01JCHECK000000000000000001",
                SecurityAction.SAVE, SecurityOutcome.NOT_ASSIGNEE);

        assertTrue(events.all().isEmpty());
    }

    // ==================== survey-media spec A-19 ====================

    @Test
    void refusedThrottled_afterRollback_isWrittenOnceAMinutePerKey_withTheRequestId() {
        MDC.put(RequestId.MDC_KEY, "01JREQUEST0000000000000001");
        TransactionSynchronizationManager.initSynchronization();

        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA_SET, CHECKLIST, SecurityAction.UPLOAD,
                SecurityOutcome.NOT_VISIBLE);

        assertEquals(List.of(), events.all(), "nothing inside the refused transaction");
        completeRolledBack();
        assertEquals(List.of(), events.all(), "not while the caller's connection may still be bound");
        recorder.flushDeferred();
        assertEquals(1, events.all().size());
        SurveySecurityEvent event = events.all().get(0);
        assertEquals("01JREQUEST0000000000000001", event.requestIdOrNull());
        assertEquals(SecurityResourceType.MEDIA_SET, event.resourceType());
        assertEquals(CHECKLIST, event.resourceId());
        assertEquals(List.of("begin:REQUIRES_NEW:READ_COMMITTED", "commit"), transactions.calls);
        assertEquals(List.of("event:lock"), journal);

        now = now.plusSeconds(59);
        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA_SET, CHECKLIST, SecurityAction.UPLOAD,
                SecurityOutcome.NOT_VISIBLE);
        assertEquals(1, events.all().size(), "the same key within a minute is not written again");

        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA_SET, CHECKLIST, SecurityAction.UPLOAD,
                SecurityOutcome.NOT_ASSIGNEE);
        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA_SET, OTHER_CHECKLIST, SecurityAction.UPLOAD,
                SecurityOutcome.NOT_VISIBLE);
        assertEquals(3, events.all().size(), "another outcome or resource is another key");

        now = now.plusSeconds(2);
        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA_SET, CHECKLIST, SecurityAction.UPLOAD,
                SecurityOutcome.NOT_VISIBLE);
        assertEquals(4, events.all().size(), "a minute later the key is written again");
    }

    @Test
    void flushDeferred_writesEachQueuedEventOnce() {
        TransactionSynchronizationManager.initSynchronization();
        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA, "01JMED1A000000000000000009",
                SecurityAction.UPLOAD, SecurityOutcome.NOT_ASSIGNEE);
        completeRolledBack();

        recorder.flushDeferred();
        recorder.flushDeferred();

        assertEquals(1, events.all().size());
    }

    @Test
    void refusedThrottled_writeFails_isSwallowedSoTheAnswerStays() {
        events.failure = new IllegalStateException("table is down");

        recorder.refusedThrottled(USER, LEAD, SecurityResourceType.MEDIA, "01JMED1A000000000000000001",
                SecurityAction.REMOVE, SecurityOutcome.NOT_ASSIGNEE);

        assertTrue(events.all().isEmpty());
    }

    @Test
    void refused_checklistEvents_areNeverThrottled() {
        recorder.refused(USER, LEAD, SecurityResourceType.CHECKLIST, CHECKLIST, SecurityAction.READ,
                SecurityOutcome.NOT_VISIBLE);
        recorder.refused(USER, LEAD, SecurityResourceType.CHECKLIST, CHECKLIST, SecurityAction.READ,
                SecurityOutcome.NOT_VISIBLE);

        assertEquals(2, events.all().size());
        assertEquals(List.of(), journal, "no throttle lock for the checklist events");
    }

    @Test
    void recordInTransaction_actionEvent_isWrittenInTheCallersTransactionUnthrottled() {
        MDC.put(RequestId.MDC_KEY, "01JREQUEST0000000000000002");
        TransactionSynchronizationManager.initSynchronization();

        recorder.recordInTransaction(USER, LEAD, SecurityResourceType.MEDIA, "01JMED1A000000000000000001",
                SecurityAction.HOLD, SecurityOutcome.HELD, null);
        recorder.recordInTransaction(USER, LEAD, SecurityResourceType.MEDIA, "01JMED1A000000000000000001",
                SecurityAction.HOLD, SecurityOutcome.HELD, null);

        assertEquals(2, events.all().size(), "written at once, inside the transaction, never throttled");
        assertEquals("01JREQUEST0000000000000002", events.all().get(0).requestIdOrNull());
        assertEquals(now, events.all().get(0).occurredAt());
        assertEquals(List.of(), transactions.calls, "no transaction of its own");
    }

    @Test
    void recordInTransaction_scan_carriesTheJobIdAsCorrelationId() {
        recorder.recordInTransaction(USER, LEAD, SecurityResourceType.MEDIA, "01JMED1A000000000000000001",
                SecurityAction.SCAN, SecurityOutcome.MALWARE_DETECTED, "42");

        assertEquals("42", events.all().get(0).correlationIdOrNull());
    }

    private static void completeRolledBack() {
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        registered.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    /** Records begin, commit and rollback; has no real resource. */
    private static final class RecordingTransactions extends AbstractPlatformTransactionManager {

        private final List<String> calls = new ArrayList<>();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            calls.add("begin:" + (definition.getPropagationBehavior()
                    == TransactionDefinition.PROPAGATION_REQUIRES_NEW ? "REQUIRES_NEW" : "OTHER")
                    + (definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
                    ? ":READ_COMMITTED" : ""));
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            calls.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            calls.add("rollback");
        }
    }
}
