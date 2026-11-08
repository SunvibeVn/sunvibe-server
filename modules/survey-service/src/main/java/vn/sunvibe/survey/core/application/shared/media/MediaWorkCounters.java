package vn.sunvibe.survey.core.application.shared.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;

/**
 * The worker's per-uploader counters of the hour (survey-media spec A-27): the processing milliseconds that order the
 * fair claim ({@code PROC_MS}) and the expensive outcomes that create and the content upload refuse at 20
 * ({@code EXPENSIVE}). Each increment is its own short READ COMMITTED transaction after the outcome commit; a failure
 * is logged with ids only and never changes the outcome.
 */
@Component
public class MediaWorkCounters {

    private static final Logger log = LoggerFactory.getLogger(MediaWorkCounters.class);

    private final MediaCountersRepository counters;
    private final TransactionTemplate ownTransaction;
    private final MediaClock clock;

    public MediaWorkCounters(MediaCountersRepository counters, PlatformTransactionManager transactionManager,
                             MediaClock clock) {
        this.counters = counters;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
    }

    /** survey-media spec A-27: the run's milliseconds, added after each outcome. */
    public void addProcessingMillis(String uploaderUserId, long millis) {
        add(uploaderUserId, MediaCounterKind.PROC_MS, Math.max(0, millis));
    }

    /** survey-media spec A-27: a processor limit hit, a scan-limit result or a decode over 30 s of wall clock. */
    public void addExpensive(String uploaderUserId) {
        add(uploaderUserId, MediaCounterKind.EXPENSIVE, 1);
    }

    private void add(String userId, MediaCounterKind kind, long amount) {
        try {
            ownTransaction.executeWithoutResult(status -> counters.add(userId, ReadBudgetArithmetic.hourStart(
                    clock.now()), kind, amount));
        } catch (RuntimeException e) {
            log.error("Could not count the {} of uploader {}", kind, userId, e);
        }
    }
}
