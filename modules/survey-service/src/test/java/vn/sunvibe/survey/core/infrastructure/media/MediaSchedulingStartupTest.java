package vn.sunvibe.survey.core.infrastructure.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.survey.core.application.service.media.MediaContentDeletionJob;
import vn.sunvibe.survey.core.application.service.media.MediaFullListingJob;
import vn.sunvibe.survey.core.application.service.media.MediaProbeRunner;
import vn.sunvibe.survey.core.application.service.media.MediaProcessingWorker;
import vn.sunvibe.survey.core.application.service.media.MediaRestoreReconciliation;
import vn.sunvibe.survey.core.application.service.media.MediaSweeper;
import vn.sunvibe.survey.core.application.shared.media.MediaIncidentRecorder;
import vn.sunvibe.survey.core.application.shared.media.MediaWorkCounters;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaRuntimeMode;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;
import vn.sunvibe.survey.core.testsupport.InMemoryIntakeHealth;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaIncidents;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;
import vn.sunvibe.survey.core.testsupport.NoListingRuns;

/**
 * What a start schedules (survey-media spec A-1, A-18, plan PD-4; SM-17, SM-32; plan Task A24): the real registrations
 * over a real media scheduler, the services built on in-memory fakes. Nothing with every adapter none (production as
 * shipped); everything with real adapters; all but the worker while processing is off; a processor self-probe that
 * fails at start stops only this instance's worker, until a later processor probe passes, and keeps intake paused;
 * probes never wait for worker slots.
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
    private static final int RETRY_ATTEMPTS = 4;
    private static final Duration LATE_START_CHECK = Duration.ofMillis(20);
    private static final String WORKER = "media-worker";
    private static final MediaRuntimeMode ALL_REAL = new MediaRuntimeMode(true, false, true, true, true);

    /** How many times the registration read whether a processor probe passed. */
    private final AtomicInteger checks = new AtomicInteger();
    private final List<Runnable> detachers = new ArrayList<>();
    /** What the worker registration writes to; the other registrations always use {@code h.health}. */
    private IntakeHealthRepository registrationHealth = h.health;
    private MediaScheduling scheduling;
    private MediaProbeRunner probes;
    private MediaWorkerRegistration registration;

    @AfterEach
    void stop() {
        if (scheduling != null) {
            scheduling.destroy();
        }
        detachers.forEach(Runnable::run);
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
        assertEquals(0, normalizer.probes.get(), "processing off: no self-probe");
    }

    /**
     * spec A-1, A-18 (code review M9, SV-069): the self-probe is retried for a bounded time before it gives up; the
     * worker then waits for a later pass (below).
     */
    @Test
    void processorSelfProbeFailsThroughEveryRetry_stopsOnlyThatWorker_intakeStaysPaused_instanceStarts()
            throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;

        List<String> tasks = start(new MediaRuntimeMode(true, false, true, true, true));
        await(() -> h.health.calls.contains("failure:PROCESSOR:pause"));

        assertEquals(List.of("media-deletion", "media-sweeper", "media-probes", "media-full-listing"), tasks);
        assertEquals(RETRY_ATTEMPTS, normalizer.probes.get(), "every attempt of the bounded retry was made");
        assertEquals(List.of("media-deletion", "media-sweeper", "media-probes", "media-full-listing"),
                scheduling.registeredTasks(), "no worker after the last attempt");
        assertTrue(h.health.row.intakePausedSince() != null, "intake stays paused");
    }

    /** code review M9 (SV-069): a processor container up a few seconds after the API still gets this instance's worker. */
    @Test
    void processorUpAfterTheApi_selfProbeRetried_workerScheduledWithoutPause() throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;
        normalizer.passAfter = 2;

        List<String> tasks = start(new MediaRuntimeMode(true, false, true, true, true));
        // the retry runs on its own thread: only membership is checked, never the order against a slow start
        assertTrue(tasks.containsAll(List.of("media-deletion", "media-sweeper", "media-probes", "media-full-listing")),
                tasks.toString());
        await(() -> scheduling.registeredTasks().contains("media-worker"));

        assertEquals(3, normalizer.probes.get());
        assertTrue(h.health.calls.stream().noneMatch(call -> call.startsWith("failure:PROCESSOR")),
                h.health.calls.toString());
    }

    /** spec amendment 2026-11-05: a processor down for hours costs no extra probe, no line per check and one pause. */
    @Test
    void givenUp_noWorker_noExtraProbe_onePause() throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;
        ListAppender<ILoggingEvent> logs = logsOf(MediaWorkerRegistration.class.getName());

        start(ALL_REAL);
        await(() -> checks.get() >= 5);

        assertEquals(RETRY_ATTEMPTS, normalizer.probes.get(), "a check reads memory and runs no probe");
        assertEquals(1, Collections.frequency(h.health.calls, "failure:PROCESSOR:pause"));
        assertFalse(scheduling.registeredTasks().contains(WORKER));
        assertEquals(1, lines(logs, Level.ERROR), "the give-up");
        assertEquals(RETRY_ATTEMPTS, logs.list.size(), "a warning per retry, the give-up and no line per check");
    }

    /** spec amendment 2026-11-05 (SV-094): the first pass after the give-up starts the worker, with no restart. */
    @Test
    void lateProcessor_runnerPass_startsTheWorkerOnce() throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;
        ListAppender<ILoggingEvent> logs = logsOf(MediaWorkerRegistration.class.getName());
        start(ALL_REAL);
        await(() -> h.health.calls.contains("failure:PROCESSOR:pause"));
        assertFalse(scheduling.registeredTasks().contains(WORKER));

        normalizer.probe = ProbeResult.PASSED;
        assertTrue(probes.runAll());
        await(() -> lines(logs, Level.INFO) == 1);

        int checksAtTheStart = checks.get();
        Thread.sleep(LATE_START_CHECK.multipliedBy(5).toMillis());
        assertEquals(1, Collections.frequency(scheduling.registeredTasks(), WORKER));
        assertEquals(1, lines(logs, Level.INFO));
        assertEquals(checksAtTheStart, checks.get(), "the checks end with the start of the worker");
        assertTrue(h.health.row.intakePausedSince() != null, "only a sweep resumes intake");
    }

    /** The pause write can fail with the database down; this instance still waits for its processor. */
    @Test
    void givenUp_pauseWriteFails_lateProcessorStillStartsTheWorker() throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;
        registrationHealth = new InMemoryIntakeHealth() {
            @Override
            public void recordFailure(PauseReason reason, boolean pause, Instant appNow) {
                throw new IllegalStateException("database down");
            }
        };
        start(ALL_REAL);
        await(() -> checks.get() >= 1);

        normalizer.probe = ProbeResult.PASSED;
        assertTrue(probes.runAll());

        await(() -> scheduling.registeredTasks().contains(WORKER));
    }

    /** spec amendment 2026-11-05: only a pass after the start-up retries counts, not one the last attempts disproved. */
    @Test
    void givenUp_passBeforeTheGiveUp_startsNothing() throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;
        normalizer.onSecondAttempt = () -> {
            normalizer.probe = ProbeResult.PASSED;
            assertTrue(probes.runAll());
            normalizer.probe = ProbeResult.INCOMPLETE;
        };

        start(ALL_REAL);
        await(() -> checks.get() >= 3);

        assertFalse(scheduling.registeredTasks().contains(WORKER));
    }

    @Test
    void awaitProcessor_afterShutdown_stopsQuietly() {
        normalizer.probe = ProbeResult.INCOMPLETE;
        ListAppender<ILoggingEvent> logs = logsOf(MediaWorkerRegistration.class.getName());
        start(ALL_REAL);
        Instant since = h.clock.now();
        scheduling.destroy();

        registration.awaitProcessor(since);

        normalizer.probe = ProbeResult.PASSED;
        assertTrue(probes.runAll());
        registration.awaitProcessor(since);

        assertFalse(scheduling.registeredTasks().contains(WORKER));
        assertEquals(0, lines(logs, Level.INFO), "no word of a worker that the stopped scheduler refused");
    }

    /** A probe can be in flight at shutdown for up to 160 s; the next retry is then refused by a stopped scheduler. */
    @Test
    void probeInFlightAtShutdown_incomplete_stopsQuietly() throws Exception {
        assertStopsQuietlyWithAProbeInFlight(ProbeResult.INCOMPLETE);
    }

    /** As above, and the start of the worker is what the stopped scheduler refuses. */
    @Test
    void probeInFlightAtShutdown_passing_stopsQuietly() throws Exception {
        assertStopsQuietlyWithAProbeInFlight(ProbeResult.PASSED);
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
        MediaProbeRunner runner = new MediaProbeRunner(h.store, scanner, normalizer, h.health, h.clock);

        long started = System.nanoTime();
        boolean passed = runner.runAll();
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        release.countDown();

        assertTrue(passed);
        assertTrue(took.compareTo(Duration.ofSeconds(2)) < 0, "the probes waited for no worker slot: " + took);
    }

    // ==================== helpers ====================

    /**
     * Attempt 2 of the self-probe is held on its scheduler thread while the application stops: attempt 1 runs on this
     * thread inside {@code register()}, and the last attempt logs the give-up error of a start that failed.
     */
    private void assertStopsQuietlyWithAProbeInFlight(ProbeResult answer) throws Exception {
        normalizer.probe = ProbeResult.INCOMPLETE;
        normalizer.holdAttempt = 2;
        normalizer.heldAnswer = answer;
        ListAppender<ILoggingEvent> everything = logsOf(Logger.ROOT_LOGGER_NAME);
        start(ALL_REAL);
        assertTrue(normalizer.held.await(10, TimeUnit.SECONDS), "attempt 2 is in flight");

        scheduling.destroy();
        Thread held = normalizer.heldThread;
        held.join(5_000);

        assertFalse(held.isAlive(), "destroy does not wait, so the thread is joined here");
        assertEquals(0, lines(everything, Level.ERROR), everything.list.toString());
        assertEquals(2, normalizer.probes.get(), "no attempt after the shutdown");
        assertFalse(scheduling.registeredTasks().contains(WORKER));
    }

    private ListAppender<ILoggingEvent> logsOf(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        logger.addAppender(appender);
        detachers.add(() -> logger.detachAppender(appender));
        return appender;
    }

    private static long lines(ListAppender<ILoggingEvent> logs, Level level) {
        return logs.list.stream().filter(event -> event.getLevel() == level).count();
    }

    /** The registrations of a start, in the order Spring creates them; returns the scheduled task names. */
    private List<String> start(MediaRuntimeMode mode) {
        h.health.pause(vn.sunvibe.survey.core.domain.model.media.PauseReason.NOT_STARTED, Instant.now());
        MediaProperties properties = new MediaProperties();
        properties.getHousekeeping().setSweepStartDelay(Duration.ofHours(1));
        properties.getHousekeeping().setListingPoll(Duration.ofHours(1));
        properties.getHousekeeping().setDeletionPoll(Duration.ofHours(1));
        properties.getHousekeeping().setProbePoll(Duration.ofHours(1));
        scheduling = new MediaScheduling(mode, 2);
        probes = new MediaProbeRunner(h.store, scanner, normalizer, h.health, h.clock) {
            @Override
            public boolean processorPassedSince(Instant since) {
                checks.incrementAndGet();
                return super.processorPassedSince(since);
            }
        };
        MediaProcessingWorker worker = new MediaProcessingWorker(h.jobs, h.media, h.sets, h.variants,
                h.base.checklists, h.store, scanner, normalizer, h.health, probes,
                new MediaWorkCounters(h.counters, h.transactions, h.clock), h.recorder, h.clock,
                () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(), h.transactions);
        MediaIncidentRecorder incidents = new MediaIncidentRecorder(new InMemoryMediaIncidents(), h.transactions);
        MediaSweeper sweeper = new MediaSweeper(h.media, h.sets, h.jobs, h.variants, h.store, h.counters, h.slots,
                h.health, incidents, h.switches, probes, new NoListingRuns(), h.clock, h.transactions);
        MediaFullListingJob listing = new MediaFullListingJob(new NoListingRuns(), h.variants, h.store, incidents,
                h.health, h.clock, () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(), probes);
        MediaRestoreReconciliation reconciliation = new MediaRestoreReconciliation(h.media, h.setLocks, h.variants,
                h.store, h.clock, h.transactions, false, false);
        MediaContentDeletionJob deletion = new MediaContentDeletionJob(h.jobs, h.media, h.setLocks, h.references, h.store, h.health,
                h.clock, () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(), h.transactions);

        registration = new MediaWorkerRegistration(scheduling, worker, normalizer, probes, registrationHealth, h.clock,
                RETRY_ATTEMPTS, Duration.ofMillis(20), LATE_START_CHECK);
        registration.register();
        new MediaDeletionRegistration(scheduling, deletion, properties).register();
        new MediaHousekeepingRegistration(scheduling, sweeper, probes, listing, reconciliation, properties)
                .register();
        return scheduling.registeredTasks();
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "within 10 s");
    }

    private static final class Normalizer implements ImageNormalizer {
        volatile ProbeResult probe = ProbeResult.PASSED;
        /** When set, the probe passes after this many answers of {@link #probe}. */
        volatile int passAfter = -1;
        /** When set, this attempt (from 1) blocks until its thread is interrupted, then gives {@link #heldAnswer}. */
        volatile int holdAttempt = -1;
        volatile ProbeResult heldAnswer;
        volatile Thread heldThread;
        final CountDownLatch held = new CountDownLatch(1);
        /** Runs once, inside attempt 2, before it answers. */
        volatile Runnable onSecondAttempt = () -> { };
        final AtomicInteger probes = new AtomicInteger();

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
            int answered = probes.getAndIncrement();
            if (answered + 1 == holdAttempt) {
                return holdUntilInterrupted();
            }
            if (answered + 1 == 2) {
                onSecondAttempt.run();
            }
            return passAfter >= 0 && answered >= passAfter ? ProbeResult.PASSED : probe;
        }

        private ProbeResult holdUntilInterrupted() {
            heldThread = Thread.currentThread();
            held.countDown();
            try {
                new CountDownLatch(1).await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return heldAnswer;
        }

        @Override
        public String processorVersion() {
            return "test-9";
        }
    }
}
