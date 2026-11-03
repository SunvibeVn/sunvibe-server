package vn.sunvibe.survey.core.application.shared;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.application.exception.MediaRateLimitedException;
import vn.sunvibe.survey.core.application.exception.MediaServerBusyException;
import vn.sunvibe.survey.core.domain.policy.media.MediaLimits;
import vn.sunvibe.survey.core.domain.repository.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaBodySlotDomainRepository;

/**
 * The body slots of content uploads (survey-media spec A-3, 4.4 step 2; SV-017): at most 3 bodies per user, exact
 * across instances (a row of {@code survey_media_body_slots} inserted under the user's advisory lock, expiring 11
 * minutes later so a crashed instance never locks a user out), and at most N per instance (an in-memory semaphore).
 * Taken after the authority checks and before any byte is read or drained; freed when the upload ends.
 */
@Component
public class MediaBodySlots {

    /** spec A-3: a slot row expires 11 minutes after it was taken (the 10-minute body deadline and a margin). */
    public static final Duration SLOT_LIFETIME = Duration.ofMinutes(11);
    /** The wait a refused upload is told; a body slot frees within seconds for photos of usual size. */
    public static final long RETRY_AFTER_SECONDS = 10;

    private static final Logger log = LoggerFactory.getLogger(MediaBodySlots.class);

    private final SurveyMediaBodySlotDomainRepository slots;
    private final MediaCountersRepository counters;
    private final MediaClock clock;
    private final TransactionTemplate ownTransaction;
    private final MediaInstanceSettings instance;
    private final Semaphore instanceBodies;

    public MediaBodySlots(SurveyMediaBodySlotDomainRepository slots, MediaCountersRepository counters,
                          MediaClock clock, PlatformTransactionManager transactionManager,
                          MediaInstanceSettings instance) {
        this.slots = slots;
        this.counters = counters;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ownTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.instance = instance;
        this.instanceBodies = new Semaphore(instance.bodiesPerInstance());
    }

    /**
     * A user slot (429 RATE_LIMITED field {@code bodies} when the user has all of theirs) and an instance slot (503
     * MEDIA_SERVER_BUSY when the instance is full), both with {@code Retry-After}.
     */
    public Lease acquire(String userId) {
        Long slotId = ownTransaction.execute(status -> {
            counters.lockUser(userId);
            Instant now = clock.now();
            if (MediaLimits.bodiesFull(slots.countLive(userId, now), instance.bodiesPerUser())) {
                throw new MediaRateLimitedException("bodies", RETRY_AFTER_SECONDS);
            }
            return slots.take(userId, instance.instanceId(), now, now.plus(SLOT_LIFETIME));
        });
        if (!instanceBodies.tryAcquire()) {
            free(slotId);
            throw new MediaServerBusyException(RETRY_AFTER_SECONDS);
        }
        return new Lease(slotId);
    }

    private void free(long slotId) {
        try {
            ownTransaction.executeWithoutResult(status -> slots.free(slotId));
        } catch (RuntimeException e) {
            // Ids only; an unfreed row expires after SLOT_LIFETIME
            log.error("Could not free survey media body slot {} of instance {}", slotId, instance.instanceId(), e);
        }
    }

    /** Both slots of one upload; closing frees them. */
    public final class Lease implements AutoCloseable {

        private final long slotId;
        private boolean closed;

        private Lease(long slotId) {
            this.slotId = slotId;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                instanceBodies.release();
                free(slotId);
            }
        }
    }
}
