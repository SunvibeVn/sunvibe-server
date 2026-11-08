package vn.sunvibe.survey.core.infrastructure.media;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.application.service.media.MediaProbeRunner;
import vn.sunvibe.survey.core.application.service.media.MediaProcessingWorker;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;

/**
 * Puts the processing worker on the media scheduler (survey-media plan PD-4, survey-media spec A-1): only while processing is
 * effectively on, and only after the processor's self-probe passed. A failing self-probe is not a configuration error:
 * the instance starts and processes nothing while the probe is retried every 5 seconds for about a minute (a processor
 * container that starts a little after the API, code review M9); when every attempt failed, a PROCESSOR pause is
 * recorded (intake stays paused) and this instance's worker stays off until a processor probe of this instance passes,
 * then starts with no restart. Every poll hands each free worker thread a run that keeps claiming while jobs are due,
 * so the bounded worker executor never queues more work than it has threads for.
 */
@Component
public class MediaWorkerRegistration {

    /** How often idle worker threads look for due jobs. */
    static final Duration POLL = Duration.ofSeconds(5);
    /** The self-probe attempts at start-up and their spacing: about a minute in all. */
    static final int SELF_PROBE_ATTEMPTS = 12;
    static final Duration SELF_PROBE_RETRY = Duration.ofSeconds(5);
    /** After the last self-probe failed: how often this instance reads whether a later processor probe passed. */
    static final Duration LATE_START_CHECK = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(MediaWorkerRegistration.class);

    private final MediaScheduling scheduling;
    private final MediaProcessingWorker worker;
    private final ImageNormalizer normalizer;
    private final MediaProbeRunner probes;
    private final IntakeHealthRepository health;
    private final MediaClock clock;
    private final int selfProbeAttempts;
    private final Duration selfProbeRetry;
    private final Duration lateStartCheck;

    @Autowired
    public MediaWorkerRegistration(MediaScheduling scheduling, MediaProcessingWorker worker, ImageNormalizer normalizer,
                                   MediaProbeRunner probes, IntakeHealthRepository health, MediaClock clock) {
        this(scheduling, worker, normalizer, probes, health, clock, SELF_PROBE_ATTEMPTS, SELF_PROBE_RETRY,
                LATE_START_CHECK);
    }

    MediaWorkerRegistration(MediaScheduling scheduling, MediaProcessingWorker worker, ImageNormalizer normalizer,
                            MediaProbeRunner probes, IntakeHealthRepository health, MediaClock clock,
                            int selfProbeAttempts, Duration selfProbeRetry, Duration lateStartCheck) {
        this.scheduling = scheduling;
        this.worker = worker;
        this.normalizer = normalizer;
        this.probes = probes;
        this.health = health;
        this.clock = clock;
        this.selfProbeAttempts = selfProbeAttempts;
        this.selfProbeRetry = selfProbeRetry;
        this.lateStartCheck = lateStartCheck;
    }

    @PostConstruct
    void register() {
        if (!scheduling.processingOn()) {
            return;
        }
        selfProbe(1);
    }

    /** survey-media spec A-1, A-18: the processor answers its probe before this instance processes anything. */
    private void selfProbe(int attempt) {
        ProbeResult result;
        try {
            result = normalizer.probe();
        } catch (RuntimeException e) {
            result = ProbeResult.INCOMPLETE;
        }
        if (result == ProbeResult.PASSED) {
            startWorker();
            return;
        }
        if (attempt < selfProbeAttempts) {
            log.warn("The survey media processor self-probe failed ({}), attempt {} of {}; this instance processes"
                    + " nothing until it passes", result, attempt, selfProbeAttempts);
            runLater(() -> selfProbe(attempt + 1), selfProbeRetry);
            return;
        }
        Instant now = clock.now();
        log.error("The survey media processor self-probe failed {} times at start-up ({}); this instance processes"
                + " nothing", selfProbeAttempts, result);
        try {
            health.recordFailure(PauseReason.PROCESSOR, true, now);
        } catch (RuntimeException e) {
            log.error("Could not record the processor self-probe failure", e);
        }
        runLater(() -> awaitProcessor(now), lateStartCheck);
    }

    /**
     * survey-media spec amendment 2026-11-05: the worker stays off only until a processor probe of this instance
     * passes. This reads what the probe runner remembers and runs no probe of its own, so survey-media spec A-18 still holds.
     */
    void awaitProcessor(Instant since) {
        if (!probes.processorPassedSince(since)) {
            runLater(() -> awaitProcessor(since), lateStartCheck);
            return;
        }
        if (startWorker()) {
            log.info("The survey media processor passed a probe after the start-up retries; this instance now"
                    + " processes");
        }
    }

    /** False when the application stopped meanwhile: a probe can be in flight at shutdown for up to 160 s. */
    private boolean startWorker() {
        try {
            scheduling.scheduleWorker("media-worker", this::dispatch, POLL);
            return true;
        } catch (RejectedExecutionException e) {
            log.debug("The media scheduler stopped before the worker could start");
            return false;
        }
    }

    private void runLater(Runnable task, Duration delay) {
        try {
            scheduling.runOnceAfter(task, delay);
        } catch (RejectedExecutionException e) {
            log.debug("The media scheduler stopped; the wait for the processor ends");
        }
    }

    void dispatch() {
        for (int free = scheduling.freeWorkerThreads(); free > 0; free--) {
            try {
                scheduling.workerExecutor().execute(this::drain);
            } catch (RejectedExecutionException e) {
                return;
            }
        }
    }

    private void drain() {
        try {
            while (worker.runOnce()) {
                // keep claiming while jobs are due
            }
        } catch (RuntimeException e) {
            log.error("The survey media worker stopped a run", e);
        }
    }
}
