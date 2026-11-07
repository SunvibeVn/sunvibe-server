package vn.sunvibe.survey.core.application.shared.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.logging.RequestId;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.common.SurveySecurityEventDomainRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.time.temporal.ChronoUnit;

/**
 * Writes the security events of the checklist boundary (survey-checklist BR-20, E-2, spec 4.10, A-24, PD-6): after the
 * refused request's transaction has completed (also a rollback), in a short transaction of their own, so the refusal's
 * rollback cannot remove them. Outside a transaction they are written at once. A failing write is logged with ids only
 * and never changes the answer.
 * <p>
 * Survey-media events (spec A-19, 4.9) also store the server request id. Its refusal events are throttled: at most
 * one per user, resource, action and outcome a minute, decided under that key's advisory lock in their own READ
 * COMMITTED transaction; a writer that finds the key's lock taken skips, so refusals never queue on it, and the media
 * services write them once their own transaction has released its connection. Its action events (HOLD, RELEASE, SCAN)
 * are written in the caller's transaction, never throttled, so they exist exactly when the action committed.
 */
@Component
public class SecurityEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);
    /** Throttled refusal events of this thread, written by {@link #flushDeferred()} (spec A-19). */
    private static final ThreadLocal<List<SurveySecurityEvent>> DEFERRED = ThreadLocal.withInitial(ArrayList::new);
    /** spec A-19: one refusal event of a key a minute. */
    private static final Duration THROTTLE = Duration.ofMinutes(1);

    private final SurveySecurityEventDomainRepository events;
    private final MediaCountersRepository counters;
    private final TransactionTemplate ownTransaction;
    private final TransactionTemplate throttledTransaction;
    private final IdGenerator<Long> snowflakes;
    private final MediaClock clock;

    public SecurityEventRecorder(SurveySecurityEventDomainRepository events, MediaCountersRepository counters,
                                 PlatformTransactionManager transactionManager,
                                 @Qualifier("Snowflake") IdGenerator<Long> snowflakes, MediaClock clock) {
        this.events = events;
        this.counters = counters;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.throttledTransaction = new TransactionTemplate(transactionManager);
        this.throttledTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.throttledTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.snowflakes = snowflakes;
        this.clock = clock;
    }

    /** A staff caller was refused on an existing resource of the lead (the checklist events; never throttled). */
    public void refused(String userId, String leadId, SecurityResourceType resourceType, String resourceId,
                        SecurityAction action, SecurityOutcome outcome) {
        SurveySecurityEvent event = new SurveySecurityEvent(snowflakes.generateId(), userId, leadId, resourceType,
                resourceId, action, outcome, CorrelationId.current(),
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS));
        afterCompletion(() -> write(event));
    }

    /**
     * survey-media spec A-19: a refusal a caller can provoke (NOT_VISIBLE, NOT_ASSIGNEE, BUDGET_EXCEEDED), written
     * after completion and only when no event of the same user, resource, action and outcome exists in the last
     * minute.
     */
    public void refusedThrottled(String userId, String leadId, SecurityResourceType resourceType, String resourceId,
                                 SecurityAction action, SecurityOutcome outcome) {
        SurveySecurityEvent event = mediaEvent(userId, leadId, resourceType, resourceId, action, outcome,
                CorrelationId.current());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // written by the media service once its transaction has released its connection: a refusal never needs a
            // second pooled connection while it holds one, so scripted refusals cannot starve the pool
            DEFERRED.get().add(event);
        } else {
            writeThrottled(event);
        }
    }

    /**
     * Writes this thread's deferred refusal events, each throttled in its own short transaction (spec A-19). The media
     * services call it after their transactions, whatever the outcome.
     */
    public void flushDeferred() {
        List<SurveySecurityEvent> pending = DEFERRED.get();
        if (pending.isEmpty()) {
            return;
        }
        List<SurveySecurityEvent> writing = List.copyOf(pending);
        DEFERRED.remove();
        writing.forEach(this::writeThrottled);
    }

    /**
     * survey-media spec A-19: an action event (HOLD, RELEASE, SCAN) in the caller's transaction, never throttled; the
     * worker passes its job id as the correlation id.
     */
    public void recordInTransaction(String userId, String leadId, SecurityResourceType resourceType,
                                    String resourceId, SecurityAction action, SecurityOutcome outcome,
                                    String correlationIdOrNull) {
        events.save(mediaEvent(userId, leadId, resourceType, resourceId, action, outcome,
                correlationIdOrNull == null ? CorrelationId.current() : correlationIdOrNull));
    }

    private SurveySecurityEvent mediaEvent(String userId, String leadId, SecurityResourceType resourceType,
                                           String resourceId, SecurityAction action, SecurityOutcome outcome,
                                           String correlationIdOrNull) {
        return new SurveySecurityEvent(snowflakes.generateId(), userId, leadId, resourceType, resourceId, action,
                outcome, correlationIdOrNull, clock.now(), RequestId.current());
    }

    private static void afterCompletion(Runnable write) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    private void write(SurveySecurityEvent event) {
        try {
            ownTransaction.executeWithoutResult(status -> events.save(event));
        } catch (RuntimeException e) {
            // Ids only: the answer stays the plain 404 or 403 (spec A-24)
            log.error("Could not write survey security event {} for lead {}", event.id(), event.leadId(), e);
        }
    }

    private void writeThrottled(SurveySecurityEvent event) {
        try {
            throttledTransaction.executeWithoutResult(status -> {
                // spec A-19: a writer of the same key holds the lock and writes or finds the event: skip, never wait
                if (counters.tryLockEventKey(event.userId(), event.resourceId(), event.action(), event.outcome())
                        && !counters.hasRecentEvent(event.userId(), event.resourceId(), event.action(), event.outcome(),
                        event.occurredAt().minus(THROTTLE))) {
                    events.save(event);
                }
            });
        } catch (RuntimeException e) {
            // Ids only: the answer stays the plain 404 or 403 (survey-media spec A-19)
            log.error("Could not write survey media security event {} for lead {}", event.id(), event.leadId(), e);
        }
    }
}
