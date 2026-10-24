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
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.testsupport.InMemoryChecklistRepositories;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security events are written after the refused request's transaction ended, in a short transaction of their own; a
 * failing write never changes the answer (survey-checklist spec 4.10, A-24, PD-6).
 */
class SecurityEventRecorderTest {

    private static final String USER = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";

    private final InMemoryChecklistRepositories.SecurityEvents events = new InMemoryChecklistRepositories.SecurityEvents();
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final SecurityEventRecorder recorder = new SecurityEventRecorder(events, transactions,
            new AtomicLong(1L)::incrementAndGet);

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
                    == TransactionDefinition.PROPAGATION_REQUIRES_NEW ? "REQUIRES_NEW" : "OTHER"));
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
