package vn.sunvibe.survey.core.infrastructure.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.application.service.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.service.MediaFullListingJob;
import vn.sunvibe.survey.core.application.service.MediaProbeRunner;
import vn.sunvibe.survey.core.application.service.MediaProcessingWorker;
import vn.sunvibe.survey.core.application.service.MediaRestoreReconciliation;
import vn.sunvibe.survey.core.application.service.MediaSweeper;
import vn.sunvibe.survey.core.application.shared.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.MediaWorkCounters;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaRuntimeMode;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaIncidents;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;
import vn.sunvibe.survey.core.testsupport.NoListingRuns;

/**
 * What a start schedules (survey-media spec A-1, A-18, plan PD-4; SM-17, SM-32; plan Task A24): the real registrations
 * over a real media scheduler, the services built on in-memory fakes. Nothing with every adapter none (production as
 * shipped); everything with real adapters; all but the worker while processing is off; a processor self-probe that
 * fails at start stops only this instance's worker and keeps intake paused; probes never wait for worker slots.
 */
class MediaSchedulingStartupTest {

    private final MediaServiceHarness h = new MediaServiceHarness();
    private final Normalizer normalizer = new Normalizer();
    private final MalwareScanner scanner = new MalwareScanner() {
        @Override
        public ScanVerdict scan(InputStream content) {
            return ScanVerdict.CLEAN;
        }

        @Override
        public ProbeResult probe(Instant now) {
            return ProbeResult.PASSED;
        }
    };
    private MediaScheduling scheduling;

    @AfterEach
    void stop() {
        if (scheduling != null) {
            scheduling.destroy();
        }
    }

    @Test
    void prodAsShipped_schedulesNoProbeWorkerSweeperOrJob() {
        assertEquals(List.of(), start(new MediaRuntimeMode(false, true, false, false, true)));
    }

    @Test
    void processingOnEveryAdapterNone_schedulesNothing() {
        assertEquals(List.of(), start(new MediaRuntimeMode(true, true, false, false, true)));
    }

    @Test
    void allRealAdapters_schedulesAll() {
        assertEquals(List.of("media-worker", "media-deletion", "media-sweeper", "media-probes", "media-full-listing"),
                start(new MediaRuntimeMode(true, false, true, true, true)));
    }

    @Test
    void processingOffRealAdapters_schedulesAllButWorker() {
        assertEquals(List.of("media-deletion", "media-sweeper", "media-probes", "media-full-listing"),
                start(new MediaRuntimeMode(true, false, true, false, true)));
    }

    @Test
    void processorSelfProbeFailsAtStart_stopsOnlyThatWorker_intakeStaysPaused_instanceStarts() {
        normalizer.probe = ProbeResult.INCOMPLETE;

        List<String> tasks = start(new MediaRuntimeMode(true, false, true, true, true));

        assertEquals(List.of("media-deletion", "media-sweeper", "media-probes", "media-full-listing"), tasks);
        assertTrue(h.health.calls.contains("failure:PROCESSOR:pause"), h.health.calls.toString());
        assertTrue(h.health.row.intakePausedSince() != null, "intake stays paused");
    }

    @Test
    void probeCompletesWithinLimit_whileEveryWorkerSlotAndScannerConnectionBusy() throws Exception {
        scheduling = new MediaScheduling(new MediaRuntimeMode(true, false, true, true, true), 2);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < 2; i++) {
            scheduling.workerExecutor().execute(() -> {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertEquals(0, scheduling.freeWorkerThreads(), "every worker slot is busy");
        MediaProbeRunner probes = new MediaProbeRunner(h.store, scanner, normalizer, h.health, h.clock);

        long started = System.nanoTime();
        boolean passed = probes.runAll();
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        release.countDown();

        assertTrue(passed);
        assertTrue(took.compareTo(Duration.ofSeconds(2)) < 0, "the probes waited for no worker slot: " + took);
    }

    // ==================== helpers ====================

    /** The registrations of a start, in the order Spring creates them; returns the scheduled task names. */
    private List<String> start(MediaRuntimeMode mode) {
        h.health.pause(vn.sunvibe.survey.core.domain.model.media.PauseReason.NOT_STARTED, Instant.now());
        MediaProperties properties = new MediaProperties();
        properties.getHousekeeping().setSweepStartDelay(Duration.ofHours(1));
        properties.getHousekeeping().setListingPoll(Duration.ofHours(1));
        properties.getHousekeeping().setDeletionPoll(Duration.ofHours(1));
        properties.getHousekeeping().setProbePoll(Duration.ofHours(1));
        scheduling = new MediaScheduling(mode, 2);
        MediaProbeRunner probes = new MediaProbeRunner(h.store, scanner, normalizer, h.health, h.clock);
        MediaProcessingWorker worker = new MediaProcessingWorker(h.jobs, h.media, h.sets, h.variants,
                h.base.checklists, h.store, scanner, normalizer, h.health, probes,
                new MediaWorkCounters(h.counters, h.transactions, h.clock), h.recorder, h.clock,
                () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(), h.transactions);
        MediaIncidentRecorder incidents = new MediaIncidentRecorder(new InMemoryMediaIncidents(), h.transactions);
        MediaSweeper sweeper = new MediaSweeper(h.media, h.sets, h.jobs, h.variants, h.store, h.counters, h.slots,
                h.health, incidents, h.switches, probes, new NoListingRuns(), h.clock, h.transactions);
        MediaFullListingJob listing = new MediaFullListingJob(new NoListingRuns(), h.variants, h.store, incidents,
                h.health, h.clock, () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString());
        MediaRestoreReconciliation reconciliation = new MediaRestoreReconciliation(h.media, h.sets, h.variants,
                h.store, h.clock, h.transactions, false, false);
        MediaContentDeletionJob deletion = new MediaContentDeletionJob(h.jobs, h.media, h.sets, h.store, h.health,
                h.clock, () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(), h.transactions);

        new MediaWorkerRegistration(scheduling, worker, normalizer, h.health, h.clock).register();
        new MediaDeletionRegistration(scheduling, deletion, properties).register();
        new MediaHousekeepingRegistration(scheduling, sweeper, probes, listing, reconciliation, properties)
                .register();
        return scheduling.registeredTasks();
    }

    private static final class Normalizer implements ImageNormalizer {
        volatile ProbeResult probe = ProbeResult.PASSED;

        @Override
        public Optional<SourceFormat> detect(InputStream source, long length) {
            return Optional.of(SourceFormat.JPEG);
        }

        @Override
        public NormalizeResult normalize(InputStream source, long length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProbeResult probe() {
            return probe;
        }

        @Override
        public String processorVersion() {
            return "test-9";
        }
    }
}
