package vn.sunvibe.survey.core.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ListedObject;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ObjectPage;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.ListingRun;
import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaListingRunDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaVariantDomainRepository;

/**
 * The daily full listing of {@code media/} (survey-media spec 4.4 step 4, A-18): one run at a time with a lease; pages
 * of at most 1,000 keys from the run's stored key, listed and deleted outside any transaction, each page's progress
 * written by a fenced update, so a killed run resumes and a fenced-out runner stops. It deletes orphans as the sweep
 * does (no stored variant row, over 60 minutes); an orphan over 24 hours opens or counts in ORPHAN_OVERDUE and pauses
 * intake once its page's progress is written; the orphans it could not delete stay counted in the run, which blocks a
 * resume (A-18), and a failed listing or delete asks for a store probe. A run open over 24
 * hours is abandoned with an ids-only error log. Its duration is logged (an upgrade trigger, spec 4.12).
 */
@Service
public class MediaFullListingJob {

    static final Duration LEASE = Duration.ofMinutes(5);
    static final Duration DAILY = Duration.ofHours(24);
    static final Duration ABANDON_AFTER = Duration.ofHours(24);
    static final Duration ORPHAN_AGE = Duration.ofMinutes(60);
    static final Duration ORPHAN_OVERDUE = Duration.ofHours(24);
    private static final int PAGE = 1000;
    /** Orphans logged by id per page when deletes fail; the page's count follows (migration re-review Minor 2). */
    private static final int LOGGED_PER_PAGE = 10;
    private static final java.util.regex.Pattern MEDIA_ID = java.util.regex.Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");

    private static final Logger log = LoggerFactory.getLogger(MediaFullListingJob.class);

    private final SurveyMediaListingRunDomainRepository runs;
    private final SurveyMediaVariantDomainRepository variants;
    private final MediaObjectStore store;
    private final MediaIncidentRecorder incidents;
    private final IntakeHealthRepository health;
    private final MediaClock clock;
    private final IdGenerator<String> ulids;
    private final MediaProbeRequests probes;

    public MediaFullListingJob(SurveyMediaListingRunDomainRepository runs, SurveyMediaVariantDomainRepository variants,
                               MediaObjectStore store, MediaIncidentRecorder incidents, IntakeHealthRepository health,
                               MediaClock clock, @Qualifier("Ulid") IdGenerator<String> ulids,
                               MediaProbeRequests probes) {
        this.runs = runs;
        this.variants = variants;
        this.store = store;
        this.incidents = incidents;
        this.health = health;
        this.clock = clock;
        this.ulids = ulids;
        this.probes = probes;
    }

    /** The scheduled poll: a failing run is logged; its lease runs out and the next poll resumes it. */
    public void runQuietly() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("The survey media full listing stopped a run", e);
        }
    }

    /** Abandons stale runs, then resumes the open run or starts the daily one; false when nothing was due. */
    public synchronized boolean runOnce() {
        Instant now = clock.now();
        List<Long> abandoned = runs.abandonStartedBefore(now.minus(ABANDON_AFTER), now);
        if (!abandoned.isEmpty()) {
            log.error("Survey media full listing run(s) {} abandoned after 24 hours", abandoned);
        }
        String token = ulids.generateId();
        Optional<ListingRun> run = runs.leaseOpen(token, now.plus(LEASE), now);
        if (run.isEmpty()) {
            run = runs.startIfDue(token, now.plus(LEASE), now.minus(DAILY), now);
        }
        if (run.isEmpty()) {
            return false;
        }
        list(run.get(), token);
        return true;
    }

    private void list(ListingRun run, String token) {
        long started = System.nanoTime();
        String after = run.startAfterKeyOrNull();
        // one entry per photo, not per variant key (migration re-review of SV-069)
        Set<String> overdue = new LinkedHashSet<>();
        while (true) {
            Instant now = clock.now();
            ObjectPage page;
            try {
                page = store.list(Bucket.MEDIA, "media/", after, PAGE);
            } catch (StoreUnavailableException e) {
                // survey-media spec A-18: a store failure asks for a store probe
                probes.requestStoreProbe();
                log.warn("Survey media full listing run {} could not list ({}); it resumes later", run.id(), e.code());
                return;
            }
            List<ListedObject> old = page.objects().stream().filter(object -> object.lastModified() != null
                    && !object.lastModified().isAfter(now.minus(ORPHAN_AGE))).toList();
            Set<String> stored = variants.findStoredKeys(old.stream().map(ListedObject::key).toList());
            int undeleted = 0;
            int overdueBefore = overdue.size();
            for (ListedObject object : old) {
                if (stored.contains(object.key())) {
                    continue;
                }
                if (!object.lastModified().isAfter(now.minus(ORPHAN_OVERDUE))) {
                    overdue.add(mediaIdOf(object.key()));
                }
                try {
                    store.delete(Bucket.MEDIA, object.key());
                } catch (StoreUnavailableException e) {
                    if (undeleted == 0) {
                        probes.requestStoreProbe();
                    }
                    undeleted++;
                    if (undeleted <= LOGGED_PER_PAGE) {
                        // migration review I-2: which photo's objects stayed, by id and code only (never the key)
                        log.warn("Survey media full listing run {} could not delete an orphan of photo {} ({})",
                                run.id(), mediaIdOf(object.key()), e.code());
                    }
                }
            }
            if (undeleted > LOGGED_PER_PAGE) {
                log.warn("Survey media full listing run {} could not delete {} orphans on this page; the first {} are"
                        + " logged", run.id(), undeleted, LOGGED_PER_PAGE);
            }
            String last = page.objects().isEmpty() ? after : page.objects().get(page.objects().size() - 1).key();
            if (!runs.progress(run.id(), token, last, page.objects().size(), undeleted, clock.now().plus(LEASE))) {
                log.warn("Survey media full listing run {} lost its lease; another runner goes on", run.id());
                return;
            }
            if (overdue.size() > overdueBefore) {
                // survey-media spec A-18, survey-media BR-18: recorded with the page that found them, so a run that
                // stops later keeps the record
                // the pause first: a failed incident write must not skip it
                Instant recorded = clock.now();
                health.recordFailure(PauseReason.DELETION_OVERDUE, true, recorded);
                incidents.overdue(MediaIncidentKind.ORPHAN_OVERDUE, recorded, new ArrayList<>(overdue), recorded);
            }
            after = last;
            if (!page.truncated() || page.objects().isEmpty()) {
                break;
            }
        }
        Instant now = clock.now();
        if (runs.finish(run.id(), token, now)) {
            log.info("Survey media full listing run {} listed to the end in {} ms", run.id(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
    }

    /** The photo id of a {@code media/} key, or {@code ?} when the segment is no ULID (never a name in a log). */
    private static String mediaIdOf(String key) {
        String[] parts = key.split("/");
        return parts.length > 1 && MEDIA_ID.matcher(parts[1]).matches() ? parts[1] : "?";
    }
}
