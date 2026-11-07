package vn.sunvibe.survey.core.application.service.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import vn.sunvibe.survey.core.application.shared.media.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.InMemoryIntakeHealth;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaIncidents;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaRepositories;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaStore;
import vn.sunvibe.survey.core.testsupport.MediaFixture;
import vn.sunvibe.survey.core.testsupport.MutableMediaSwitches;
import vn.sunvibe.survey.core.testsupport.NoListingRuns;
import vn.sunvibe.survey.core.testsupport.TestTransactions;

/**
 * The sweep (survey-media spec 4.4 step 4, A-17, A-18; SM-13, SM-14; plan Task A21) with in-memory fakes: stale
 * uploads and processing end under the set and photo locks, store deletes run outside them, and a complete sweep
 * resumes intake only with the failure sequence read before its probes.
 */
class MediaSweeperTest {

    private static final Instant T0 = MediaFixture.T0;

    private final AtomicReference<Instant> now = new AtomicReference<>(T0.plus(Duration.ofHours(2)));
    private final List<String> journal = new ArrayList<>();
    private final InMemoryMediaRepositories.Media media = new InMemoryMediaRepositories.Media(journal);
    private final InMemoryMediaRepositories.Sets sets = new InMemoryMediaRepositories.Sets(journal);
    private final InMemoryMediaRepositories.Jobs jobs = new InMemoryMediaRepositories.Jobs(journal);
    private final InMemoryMediaRepositories.Variants variants = new InMemoryMediaRepositories.Variants();
    private final InMemoryMediaRepositories.Counters counters = new InMemoryMediaRepositories.Counters(journal, media);
    private final InMemoryMediaRepositories.BodySlots slots = new InMemoryMediaRepositories.BodySlots(journal);
    private final InMemoryMediaStore store = new InMemoryMediaStore(journal);
    private final InMemoryIntakeHealth health = new InMemoryIntakeHealth().open(T0);
    private final InMemoryMediaIncidents incidents = new InMemoryMediaIncidents();
    private final MutableMediaSwitches switches = new MutableMediaSwitches();
    private final TestTransactions transactions = new TestTransactions();
    private final Scanner scanner = new Scanner();
    private final MediaProbeRunner probes = new MediaProbeRunner(store, scanner, new Normalizer(), health, now::get);
    private final NoListingRuns listingRuns = new NoListingRuns();
    private final MediaSweeper sweeper = new MediaSweeper(media, sets, jobs, variants, store, counters, slots,
            health, new MediaIncidentRecorder(incidents, transactions), switches, probes, listingRuns,
            now::get, transactions);

    @Test
    void awaitingOver60Minutes_expired_setBumped() {
        SurveyMedia awaiting = stored(MediaFixture.awaiting());
        long before = setVersion();

        sweeper.sweep();

        assertEquals(MediaStatus.UPLOAD_EXPIRED, reread(awaiting).getStatus());
        assertEquals(before + 1, setVersion());
    }

    @Test
    void receivingOver15Minutes_claimReleasedAttemptDeleted() {
        now.set(T0.plus(Duration.ofMinutes(40)));
        SurveyMedia receiving = MediaFixture.receiving();
        stored(receiving);
        put(Bucket.QUARANTINE, receiving.getReceiveKey());

        sweeper.sweep();

        assertEquals(MediaStatus.AWAITING_CONTENT, reread(receiving).getStatus());
        assertTrue(store.deleted.contains("QUARANTINE:" + receiving.getReceiveKey()));
    }

    @Test
    void processingOver50Minutes_failedSourceDeletedJobClosed() {
        now.set(T0.plus(Duration.ofMinutes(60)));
        SurveyMedia processing = stored(MediaFixture.processing());
        jobs.upsertProcess(processing.getId(), T0.plusSeconds(90), T0.plusSeconds(90));
        put(Bucket.QUARANTINE, processing.getSourceStorageKey());

        sweeper.sweep();

        SurveyMedia after = reread(processing);
        assertEquals(MediaStatus.FAILED, after.getStatus());
        assertEquals(MediaStatusReason.PROCESSING_UNAVAILABLE, after.getStatusReason());
        assertNotNull(after.getSourceDeletedAt());
        assertTrue(store.deleted.contains("QUARANTINE:" + processing.getSourceStorageKey()));
        assertTrue(jobs.findByMedia(processing.getId()).get(0).state() != MediaJobState.PENDING);
    }

    @Test
    void removedProcessingOver50Minutes_cancelled() {
        now.set(T0.plus(Duration.ofMinutes(60)));
        SurveyMedia processing = MediaFixture.processing();
        processing.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, T0.plusSeconds(120));
        stored(processing);

        sweeper.sweep();

        assertEquals(MediaStatus.CANCELLED, reread(processing).getStatus());
    }

    @Test
    void complete_resumesWithSeqReadBeforeProbes_closesIncidents() {
        health.pause(PauseReason.STORE, T0);
        incidents.recordOverdue(MediaIncidentKind.SOURCE_OVERDUE, T0, 1, T0);

        sweeper.sweep();

        assertNull(health.row.intakePausedSince(), health.calls.toString());
        assertTrue(health.calls.contains("swept"));
        assertTrue(incidents.open().isEmpty());
    }

    /** Migration review I-2: a clean sweep that may not resume says why, with no key or name. */
    @Test
    void cleanSweepWhileListingRunBlocks_staysPaused_warnsWhy() {
        health.pause(PauseReason.STORE, T0);
        listingRuns.blocks = true;
        Logger logger = (Logger) LoggerFactory.getLogger(MediaSweeper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            sweeper.sweep();
        } finally {
            logger.detachAppender(appender);
        }

        assertNotNull(health.row.intakePausedSince(), "the listing run's undeleted orphans block the resume");
        assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().contains("full listing")), appender.list.toString());
    }

    @Test
    void failureDuringSweep_noResume() {
        health.pause(PauseReason.STORE, T0);
        scanner.result = ProbeResult.INCOMPLETE;

        sweeper.sweep();

        assertNotNull(health.row.intakePausedSince(), "the probe's failure moved the sequence: no resume");
    }

    @Test
    void processingOff_recordsProcessingOff_noResume() {
        health.pause(PauseReason.STORE, T0);
        switches.processing = false;

        sweeper.sweep();

        assertTrue(health.calls.contains("failure:PROCESSING_OFF:pause"), health.calls.toString());
        assertNotNull(health.row.intakePausedSince());
    }

    @Test
    void overdueContentDeletion_opensIncidentAndPauses_heldExcluded() {
        now.set(T0.plus(Duration.ofHours(25)));
        SurveyMedia removed = MediaFixture.ready();
        removed.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, T0.plusSeconds(300));
        stored(removed);
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), vn.sunvibe.survey.core.domain.model.media.HoldReason
                .SECURITY_INCIDENT);
        held.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, T0.plusSeconds(400));
        stored(held);

        sweeper.sweep();

        assertEquals(1, incidents.open().size());
        assertEquals(MediaIncidentKind.CONTENT_DELETION_OVERDUE, incidents.open().get(0).kind());
        assertEquals(1, incidents.open().get(0).count(), "the held photo has no request");
        assertTrue(health.calls.contains("failure:DELETION_OVERDUE:pause"), health.calls.toString());
    }

    @Test
    void workerHeartbeatStaleWhileWorkDue_pauses() {
        now.set(T0.plus(Duration.ofMinutes(20)));
        SurveyMedia processing = stored(MediaFixture.processing());
        jobs.upsertProcess(processing.getId(), T0.plusSeconds(90), T0.plusSeconds(90));

        sweeper.sweep();

        assertTrue(health.calls.contains("failure:WORKER_STALE:pause"), health.calls.toString());
    }

    @Test
    void budgetRowsAndExpiredSlots_cleaned() {
        sweeper.sweep();

        assertTrue(journal.contains("budget:purge"), journal.toString());
        assertTrue(journal.contains("slot:purge"), journal.toString());
    }

    // ==================== helpers ====================

    private SurveyMedia stored(SurveyMedia photo) {
        sets.insertIgnoringExisting(MediaFixture.CHECKLIST, T0);
        media.put(photo);
        return photo;
    }

    private SurveyMedia reread(SurveyMedia photo) {
        return media.stored(photo.getId()).orElseThrow();
    }

    private long setVersion() {
        return sets.findForRead(MediaFixture.CHECKLIST).orElseThrow().getSetVersion();
    }

    private void put(Bucket bucket, String key) {
        store.putCreateOnly(bucket, key, new ByteArrayInputStream(new byte[] {1}), 1);
    }

    private static final class Scanner implements MalwareScanner {
        ProbeResult result = ProbeResult.PASSED;

        @Override
        public ScanVerdict scan(InputStream content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProbeResult probe(Instant at) {
            return result;
        }
    }

    private static final class Normalizer implements ImageNormalizer {
        @Override
        public Optional<SourceFormat> detect(InputStream source, long length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NormalizeResult normalize(InputStream source, long length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProbeResult probe() {
            return ProbeResult.PASSED;
        }

        @Override
        public String processorVersion() {
            return "test-9";
        }
    }
}
