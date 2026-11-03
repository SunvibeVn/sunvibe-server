package vn.sunvibe.survey.core.infrastructure.media;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.application.service.MediaProcessingWorker;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;

/**
 * Puts the processing worker on the media scheduler (survey-media plan PD-4, spec A-1): only while processing is
 * effectively on, and only after the processor's self-probe passed at start-up; a failing self-probe is not a
 * configuration error: it stops this instance's worker, records a PROCESSOR pause (intake stays paused) and the
 * instance starts. Every poll hands each free worker thread a run that keeps claiming while jobs are due, so the bounded
 * worker executor never queues more work than it has threads for.
 */
@Component
public class MediaWorkerRegistration {

    /** How often idle worker threads look for due jobs. */
    static final Duration POLL = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(MediaWorkerRegistration.class);

    private final MediaScheduling scheduling;
    private final MediaProcessingWorker worker;
    private final ImageNormalizer normalizer;
    private final IntakeHealthRepository health;
    private final MediaClock clock;

    public MediaWorkerRegistration(MediaScheduling scheduling, MediaProcessingWorker worker, ImageNormalizer normalizer,
                                   IntakeHealthRepository health, MediaClock clock) {
        this.scheduling = scheduling;
        this.worker = worker;
        this.normalizer = normalizer;
        this.health = health;
        this.clock = clock;
    }

    @PostConstruct
    void register() {
        if (scheduling.processingOn() && !selfProbePasses()) {
            return;
        }
        scheduling.scheduleWorker("media-worker", this::dispatch, POLL);
    }

    /** spec A-1, A-18: the processor answers its probe before this instance processes anything. */
    private boolean selfProbePasses() {
        ProbeResult result;
        try {
            result = normalizer.probe();
        } catch (RuntimeException e) {
            result = ProbeResult.INCOMPLETE;
        }
        if (result == ProbeResult.PASSED) {
            return true;
        }
        log.error("The survey media processor self-probe failed at start-up ({}); this instance processes nothing",
                result);
        try {
            health.recordFailure(PauseReason.PROCESSOR, true, clock.now());
        } catch (RuntimeException e) {
            log.error("Could not record the processor self-probe failure", e);
        }
        return false;
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
