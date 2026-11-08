package vn.sunvibe.survey.core.application.service.media;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ListedObject;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ObjectPage;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.MediaRef;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaBodySlotDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaListingRunDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;

/**
 * The sweep (survey-media spec 4.4 step 4, A-17, A-18): every 5 minutes and once at start-up, whatever processing
 * says. It reads {@code failure_seq} first; ends stale uploads and processing under the set and photo locks, one photo
 * or checklist per transaction, with store deletes outside the locks (mark, delete, confirm); deletes expired records,
 * left-over sources and orphans; records overdue deletions as incidents and pauses; records PROCESSING_OFF and stale
 * heartbeats while work is due; runs the probes; purges old budget rows and expired body slots; and, after a complete
 * sweep in which nothing failed or was overdue, resumes intake with the sequence it read first and closes the open
 * incidents. Times come from the application clock.
 */
@Service
public class MediaSweeper {

    static final Duration AWAITING_MAX = Duration.ofMinutes(60);
    static final Duration RECEIVING_MAX = Duration.ofMinutes(15);
    static final Duration PROCESSING_MAX = Duration.ofMinutes(50);
    static final Duration EXPIRED_KEPT = Duration.ofHours(24);
    static final Duration QUARANTINE_ORPHAN_AGE = Duration.ofMinutes(55);
    static final Duration SOURCE_OVERDUE = Duration.ofMinutes(60);
    static final Duration MEDIA_ORPHAN_AGE = Duration.ofMinutes(60);
    static final Duration ORPHAN_OVERDUE = Duration.ofHours(24);
    static final Duration MEDIA_WINDOW = Duration.ofHours(48);
    static final Duration DELETION_OVERDUE = Duration.ofHours(24);
    static final Duration HEARTBEAT_STALE = Duration.ofMinutes(10);
    static final Duration BUDGETS_KEPT = Duration.ofHours(2);
    static final int BATCH = 500;
    private static final int PAGE = 1000;
    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final Logger log = LoggerFactory.getLogger(MediaSweeper.class);

    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaJobDomainRepository jobs;
    private final SurveyMediaVariantDomainRepository variants;
    private final MediaObjectStore store;
    private final MediaCountersRepository counters;
    private final SurveyMediaBodySlotDomainRepository slots;
    private final IntakeHealthRepository health;
    private final MediaIncidentRecorder incidents;
    private final MediaSwitches switches;
    private final MediaProbeRunner probes;
    private final SurveyMediaListingRunDomainRepository listingRuns;
    private final MediaClock clock;
    private final TransactionTemplate transaction;

    public MediaSweeper(SurveyMediaDomainRepository media, SurveyMediaSetDomainRepository sets,
                        SurveyMediaJobDomainRepository jobs, SurveyMediaVariantDomainRepository variants,
                        MediaObjectStore store, MediaCountersRepository counters,
                        SurveyMediaBodySlotDomainRepository slots, IntakeHealthRepository health,
                        MediaIncidentRecorder incidents, MediaSwitches switches, MediaProbeRunner probes,
                        SurveyMediaListingRunDomainRepository listingRuns, MediaClock clock,
                        PlatformTransactionManager transactionManager) {
        this.media = media;
        this.sets = sets;
        this.jobs = jobs;
        this.variants = variants;
        this.store = store;
        this.counters = counters;
        this.slots = slots;
        this.health = health;
        this.incidents = incidents;
        this.switches = switches;
        this.probes = probes;
        this.listingRuns = listingRuns;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The scheduled run: a failing sweep is logged and left incomplete (no resume). */
    public void sweepQuietly() {
        try {
            sweep();
        } catch (RuntimeException e) {
            log.error("The survey media sweep stopped before it completed", e);
        }
    }

    /** One pass; true when it completed (every step ran). */
    public synchronized boolean sweep() {
        long seqAtStart = health.read().failureSeq();
        boolean clean = true;
        if (!switches.processingOn()) {
            health.recordFailure(PauseReason.PROCESSING_OFF, true, clock.now());
            clean = false;
        }
        Overdue overdue = new Overdue();
        boolean complete = step("awaiting", this::expireAwaiting)
                & step("receiving", this::endStaleReceiving)
                & step("processing", this::endStaleProcessing)
                & step("expired", this::deleteExpiredRecords)
                & step("sources", this::deleteLeftoverSources)
                & step("quarantine", () -> deleteQuarantineOrphans(overdue))
                & step("media", () -> deleteMediaOrphans(overdue))
                & step("overdue", () -> findOverdue(overdue));
        Instant now = clock.now();
        if (overdue.any()) {
            overdue.record(incidents, now);
            health.recordFailure(PauseReason.DELETION_OVERDUE, true, now);
            clean = false;
        }
        clean &= heartbeats(now);
        boolean probed = probes.runAll();
        complete &= step("budgets", () -> {
            transaction.executeWithoutResult(status -> counters.deleteBudgetsBefore(now.minus(BUDGETS_KEPT)));
            transaction.executeWithoutResult(status -> slots.deleteExpired(now));
            return true;
        });
        if (!complete) {
            return false;
        }
        health.recordSweepCompleted(clock.now());
        // survey-media spec A-18: and neither the open listing run nor the latest complete one kept orphans it could not delete
        if (clean && probed) {
            if (!listingRuns.blocksResume()) {
                health.resumeIf(seqAtStart, clock.now());
                incidents.resolveAll(clock.now());
            } else if (health.read().paused()) {
                // migration review I-2: the operator sees why a clean sweep does not resume (ids and counts only)
                log.warn("Survey media intake stays paused: a full listing run kept orphans it could not delete;"
                        + " the next run's end decides (survey_media_listing_runs)");
            }
        }
        return true;
    }

    // ==================== uploads and processing ====================

    /** AWAITING_CONTENT 60 minutes after creation → UPLOAD_EXPIRED, one checklist per transaction. */
    private boolean expireAwaiting() {
        Instant now = clock.now();
        Instant cutoff = now.minus(AWAITING_MAX);
        for (String checklistId : checklists(media.findAwaitingCreatedBefore(cutoff, BATCH))) {
            transaction.executeWithoutResult(status -> sets.lockForUpdate(checklistId).ifPresent(set -> {
                if (media.expireStale(checklistId, cutoff, now) > 0) {
                    bump(set, now);
                }
            }));
        }
        return true;
    }

    /** RECEIVING over 15 minutes: back to AWAITING_CONTENT (or UPLOAD_EXPIRED past 60 minutes), then the attempt deleted. */
    private boolean endStaleReceiving() {
        boolean ok = true;
        for (MediaRef ref : media.findReceivingStartedBefore(clock.now().minus(RECEIVING_MAX), BATCH)) {
            String attemptKey = transaction.execute(status -> {
                Instant now = clock.now();
                Locked locked = lock(ref);
                if (locked == null) {
                    return null;
                }
                SurveyMedia photo = locked.photo();
                if (photo.getStatus() != MediaStatus.RECEIVING
                        || photo.getReceiveStartedAt().isAfter(now.minus(RECEIVING_MAX))) {
                    return null;
                }
                String key = photo.getReceiveKey();
                if (!photo.getCreatedAt().isAfter(now.minus(AWAITING_MAX))) {
                    photo.expire(now);
                } else {
                    photo.releaseClaim();
                }
                media.update(photo);
                bump(locked.set(), now);
                return key;
            });
            if (attemptKey != null) {
                ok &= delete(Bucket.QUARANTINE, attemptKey);
            }
        }
        return ok;
    }

    /** PROCESSING 50 minutes after receipt: FAILED (CANCELLED when removed), its job closed, then its source deleted. */
    private boolean endStaleProcessing() {
        boolean ok = true;
        for (MediaRef ref : media.findProcessingReceivedBefore(clock.now().minus(PROCESSING_MAX), BATCH)) {
            String sourceKey = transaction.execute(status -> {
                Instant now = clock.now();
                Locked locked = lock(ref);
                if (locked == null) {
                    return null;
                }
                SurveyMedia photo = locked.photo();
                if (photo.getStatus() != MediaStatus.PROCESSING
                        || photo.getContentReceivedAt().isAfter(now.minus(PROCESSING_MAX))) {
                    return null;
                }
                boolean removed = photo.isRemoved();
                if (removed) {
                    photo.cancel(now);
                } else {
                    photo.fail(now);
                }
                media.update(photo);
                jobs.closeProcess(photo.getId(), removed ? MediaJobState.CANCELLED : MediaJobState.DONE,
                        "SOURCE_EXPIRED", now);
                bump(locked.set(), now);
                return photo.getSourceStorageKey();
            });
            if (sourceKey != null && delete(Bucket.QUARANTINE, sourceKey)) {
                confirmSourceDeleted(ref);
            } else if (sourceKey != null) {
                ok = false;
            }
        }
        return ok;
    }

    /** UPLOAD_EXPIRED records 24 hours after they finished, at most 500 per transaction (survey-media spec A-17). */
    private boolean deleteExpiredRecords() {
        Instant cutoff = clock.now().minus(EXPIRED_KEPT);
        for (String checklistId : checklists(media.findExpiredFinishedBefore(cutoff, BATCH))) {
            int deleted;
            do {
                Integer count = transaction.execute(status -> sets.lockForUpdate(checklistId).map(set -> {
                    int n = media.deleteExpiredRecords(checklistId, cutoff, BATCH);
                    if (n > 0) {
                        bump(set, clock.now());
                    }
                    return n;
                }).orElse(0));
                deleted = count == null ? 0 : count;
            } while (deleted == BATCH);
        }
        return true;
    }

    /** The sources a failed delete left after an outcome (not RECEIVING or PROCESSING). */
    private boolean deleteLeftoverSources() {
        boolean ok = true;
        for (MediaRef ref : media.findSourcesNotDeletedBefore(clock.now(), BATCH)) {
            SurveyMedia photo = media.findById(ref.mediaId()).orElse(null);
            if (photo == null || photo.getSourceStorageKey() == null || photo.getStatus() == MediaStatus.RECEIVING
                    || photo.getStatus() == MediaStatus.PROCESSING) {
                continue;
            }
            if (delete(Bucket.QUARANTINE, photo.getSourceStorageKey())) {
                confirmSourceDeleted(ref);
            } else {
                ok = false;
            }
        }
        return ok;
    }

    private void confirmSourceDeleted(MediaRef ref) {
        transaction.executeWithoutResult(status -> {
            Locked locked = lock(ref);
            if (locked == null) {
                return;
            }
            SurveyMedia photo = locked.photo();
            if (photo.getSourceStorageKey() != null && photo.getSourceDeletedAt() == null
                    && photo.getStatus() != MediaStatus.RECEIVING && photo.getStatus() != MediaStatus.PROCESSING) {
                photo.markSourceDeleted(clock.now());
                media.update(photo);
            }
        });
    }

    // ==================== orphans ====================

    /** Quarantine objects over 55 minutes that no RECEIVING or PROCESSING photo references; probe keys skipped. */
    private boolean deleteQuarantineOrphans(Overdue overdue) {
        Instant now = clock.now();
        Set<String> inUse = media.findQuarantineKeysInUse();
        boolean ok = true;
        String after = null;
        while (true) {
            ObjectPage page = store.list(Bucket.QUARANTINE, "quarantine/", after, PAGE);
            for (ListedObject object : page.objects()) {
                String key = object.key();
                if (key.startsWith("quarantine/health/") || inUse.contains(key) || object.lastModified() == null
                        || object.lastModified().isAfter(now.minus(QUARANTINE_ORPHAN_AGE))) {
                    continue;
                }
                if (!object.lastModified().isAfter(now.minus(SOURCE_OVERDUE))) {
                    overdue.sources.add(mediaIdOf(key));
                }
                ok &= delete(Bucket.QUARANTINE, key);
            }
            if (!page.truncated() || page.objects().isEmpty()) {
                return ok;
            }
            after = page.objects().get(page.objects().size() - 1).key();
        }
    }

    /**
     * {@code media/} objects over 60 minutes that no stored variant row references, listing only keys after
     * {@code media/<the ULID time of now minus 48 hours>} (media ids are time-ordered server ULIDs).
     */
    private boolean deleteMediaOrphans(Overdue overdue) {
        Instant now = clock.now();
        boolean ok = true;
        String after = "media/" + ulidTime(now.minus(MEDIA_WINDOW));
        while (true) {
            ObjectPage page = store.list(Bucket.MEDIA, "media/", after, PAGE);
            List<ListedObject> old = page.objects().stream().filter(object -> object.lastModified() != null
                    && !object.lastModified().isAfter(now.minus(MEDIA_ORPHAN_AGE))).toList();
            Set<String> stored = variants.findStoredKeys(old.stream().map(ListedObject::key).toList());
            for (ListedObject object : old) {
                if (stored.contains(object.key())) {
                    continue;
                }
                if (!object.lastModified().isAfter(now.minus(ORPHAN_OVERDUE))) {
                    overdue.orphans.add(mediaIdOf(object.key()));
                }
                ok &= delete(Bucket.MEDIA, object.key());
            }
            if (!page.truncated() || page.objects().isEmpty()) {
                return ok;
            }
            after = page.objects().get(page.objects().size() - 1).key();
        }
    }

    // ==================== health ====================

    /** survey-media spec A-18: raw sources over 60 minutes and removed content not deleted 24 hours after its request. */
    private boolean findOverdue(Overdue overdue) {
        Instant now = clock.now();
        media.findSourcesNotDeletedBefore(now.minus(SOURCE_OVERDUE), BATCH)
                .forEach(ref -> overdue.sources.add(ref.mediaId().getValue()));
        media.findDeletionsRequestedBefore(now.minus(DELETION_OVERDUE), BATCH)
                .forEach(ref -> overdue.contents.add(ref.mediaId().getValue()));
        return true;
    }

    /** survey-media spec A-18: the worker or the deletion executor silent for 10 minutes while work is due pauses intake. */
    private boolean heartbeats(Instant now) {
        IntakeHealth row = health.read();
        boolean clean = true;
        if (switches.processingOn() && stale(row.lastWorkerBeatAt(), now) && jobs.anyDue(MediaJobKind.PROCESS, now)) {
            health.recordFailure(PauseReason.WORKER_STALE, true, now);
            clean = false;
        }
        if (stale(row.lastDeletionBeatAt(), now) && jobs.anyDue(MediaJobKind.DELETE_CONTENT, now)) {
            health.recordFailure(PauseReason.DELETION_STALE, true, now);
            clean = false;
        }
        return clean;
    }

    private static boolean stale(Instant beat, Instant now) {
        return beat == null || beat.isBefore(now.minus(HEARTBEAT_STALE));
    }

    // ==================== helpers ====================

    private boolean step(String name, BooleanSupplier work) {
        try {
            return work.getAsBoolean();
        } catch (RuntimeException e) {
            log.error("Survey media sweep step {} failed", name, e);
            return false;
        }
    }

    /** A store delete outside any lock; a failure asks for a store probe and leaves the sweep incomplete. */
    private boolean delete(Bucket bucket, String key) {
        try {
            store.delete(bucket, key);
            return true;
        } catch (StoreUnavailableException e) {
            probes.requestStoreProbe();
            log.warn("Survey media sweep could not delete an object ({})", e.code());
            return false;
        }
    }

    /** survey-media spec 5.4 for a path without a lead lock: the set, then the photo; null when the photo is gone. */
    private Locked lock(MediaRef ref) {
        MediaSet set = sets.lockForUpdate(ref.checklistId()).orElse(null);
        if (set == null) {
            return null;
        }
        SurveyMedia photo = media.lockForUpdate(List.of(ref.mediaId())).stream().findFirst().orElse(null);
        return photo == null ? null : new Locked(set, photo);
    }

    private void bump(MediaSet set, Instant now) {
        set.bump(now);
        sets.update(set);
    }

    private static Set<String> checklists(List<MediaRef> refs) {
        Set<String> ids = new LinkedHashSet<>();
        refs.forEach(ref -> ids.add(ref.checklistId()));
        return ids;
    }

    /** The photo id of a store key ({@code <prefix>/<mediaId>/...}): ids only in logs. */
    private static String mediaIdOf(String key) {
        String[] parts = key.split("/");
        return parts.length > 1 ? parts[1] : "?";
    }

    /** The 10-character ULID time part of the instant (Crockford base32 of the epoch milliseconds). */
    static String ulidTime(Instant instant) {
        long millis = instant.toEpochMilli();
        char[] chars = new char[10];
        for (int i = 9; i >= 0; i--) {
            chars[i] = CROCKFORD.charAt((int) (millis & 31));
            millis >>>= 5;
        }
        return new String(chars);
    }

    private record Locked(MediaSet set, SurveyMedia photo) {
    }

    /** The overdue deletions a sweep found, by incident kind. */
    private static final class Overdue {
        final List<String> sources = new ArrayList<>();
        final List<String> contents = new ArrayList<>();
        final List<String> orphans = new ArrayList<>();

        boolean any() {
            return !sources.isEmpty() || !contents.isEmpty() || !orphans.isEmpty();
        }

        void record(MediaIncidentRecorder incidents, Instant now) {
            incidents.overdue(MediaIncidentKind.SOURCE_OVERDUE, now, List.copyOf(new LinkedHashSet<>(sources)), now);
            incidents.overdue(MediaIncidentKind.CONTENT_DELETION_OVERDUE, now, contents, now);
            incidents.overdue(MediaIncidentKind.ORPHAN_OVERDUE, now, List.copyOf(new LinkedHashSet<>(orphans)), now);
        }
    }
}
