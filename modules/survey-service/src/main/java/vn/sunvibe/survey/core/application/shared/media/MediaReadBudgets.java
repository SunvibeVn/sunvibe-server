package vn.sunvibe.survey.core.application.shared.media;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.application.exception.media.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;

/**
 * The per-user hourly read budgets (survey-media spec A-26, {@link MediaReadBudgetLimits}; by default FULL 600, PREVIEW
 * 1,200, THUMBNAIL 6,000 and the media list 1,200 an hour), in fixed clock hours with a weighted carry-over of the
 * previous hour. Counted in the database in a
 * short READ COMMITTED transaction of its own, never inside a list's repeatable-read snapshot, so the budgets are per
 * user whatever the number of instances. The first refusal of a user, kind and hour writes the one BUDGET_EXCEEDED
 * event in that transaction.
 */
@Component
public class MediaReadBudgets {

    private static final Duration HOUR = Duration.ofHours(1);

    private final MediaCountersRepository counters;
    private final SecurityEventRecorder events;
    private final MediaClock clock;
    private final TransactionTemplate ownTransaction;
    private final MediaReadBudgetLimits limits;

    public MediaReadBudgets(MediaCountersRepository counters, SecurityEventRecorder events, MediaClock clock,
                            PlatformTransactionManager transactionManager, MediaReadBudgetLimits limits) {
        this.counters = counters;
        this.events = events;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.limits = limits;
    }

    /**
     * Counts one read of the kind.
     *
     * @throws MediaRateLimitedException 429 on {@code budget} with the seconds to the next hour; nothing counted
     */
    public void count(String userId, String leadId, MediaCounterKind kind, SecurityResourceType resourceType,
                      String resourceId) {
        Instant now = clock.now();
        Instant hourStart = ReadBudgetArithmetic.hourStart(now);
        Boolean allowed = ownTransaction.execute(status -> {
            long previous = counters.counterValue(userId, hourStart.minus(HOUR), kind);
            long effective = ReadBudgetArithmetic.effectiveLimit(limits.of(kind), previous, now);
            if (!ReadBudgetArithmetic.refusesWithoutCounting(effective)
                    && counters.countIfBelow(userId, hourStart, kind, effective).isPresent()) {
                return true;
            }
            if (counters.markExceededFirst(userId, hourStart, kind, now)) {
                // survey-media spec 4.9, A-26: one event per user, kind and hour
                events.recordInTransaction(userId, leadId, resourceType, resourceId, SecurityAction.READ,
                        SecurityOutcome.BUDGET_EXCEEDED, null);
            }
            return false;
        });
        if (!Boolean.TRUE.equals(allowed)) {
            long millis = Duration.between(now, hourStart.plus(HOUR)).toMillis();
            throw new MediaRateLimitedException("budget", Math.max(1, (millis + 999) / 1000));
        }
    }
}
