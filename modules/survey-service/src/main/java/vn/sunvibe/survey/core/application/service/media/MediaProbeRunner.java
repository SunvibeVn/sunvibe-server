package vn.sunvibe.survey.core.application.service.media;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaProbeRequests;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.ProbeKind;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;

/**
 * The health probes of this instance (survey-media spec A-18): the store probe (both buckets), the scanner probe and
 * the processor probe, on their adapters' reserved probe capacity. They run in every sweep ({@link #runAll}) and, when
 * a store-side failure or a silent scanner asked for one ({@link MediaProbeRequests}), from {@link #poll}: one probe at
 * a time and at most once a minute per kind, besides the re-run of an incomplete probe within a minute. A wrong answer
 * pauses intake at once, and a wrong scanner answer also blocks every scan until a scanner probe passes; an incomplete
 * probe bumps {@code failure_seq} and pauses only on its second in a row. Probes write no security event and log no
 * signature name.
 */
@Service
public class MediaProbeRunner implements MediaProbeRequests {

    /** spec A-18: a requested probe runs at most once a minute per kind and instance. */
    static final Duration MIN_INTERVAL = Duration.ofMinutes(1);
    /** spec A-18: an incomplete probe is re-run within a minute (the poll runs every 10 seconds). */
    static final Duration RERUN_AFTER = Duration.ofSeconds(40);

    private static final Logger log = LoggerFactory.getLogger(MediaProbeRunner.class);

    private final MediaObjectStore store;
    private final MalwareScanner scanner;
    private final ImageNormalizer normalizer;
    private final IntakeHealthRepository health;
    private final MediaClock clock;
    private final ReentrantLock oneAtATime = new ReentrantLock();
    private final AtomicBoolean storeRequested = new AtomicBoolean();
    private final AtomicBoolean scannerRequested = new AtomicBoolean();
    private final Map<ProbeKind, Instant> lastRun = new EnumMap<>(ProbeKind.class);
    private final Map<ProbeKind, Integer> incompleteInARow = new EnumMap<>(ProbeKind.class);
    /** When a processor probe of this instance last passed; the worker registration reads it from its own thread. */
    private volatile Instant lastProcessorPass;

    public MediaProbeRunner(MediaObjectStore store, MalwareScanner scanner, ImageNormalizer normalizer,
                            IntakeHealthRepository health, MediaClock clock) {
        this.store = store;
        this.scanner = scanner;
        this.normalizer = normalizer;
        this.health = health;
        this.clock = clock;
    }

    @Override
    public void requestStoreProbe() {
        storeRequested.set(true);
    }

    @Override
    public void requestScannerProbe() {
        scannerRequested.set(true);
    }

    /** Whether a store probe was asked for and has not run yet (operations and tests read it). */
    public boolean isStoreProbeRequested() {
        return storeRequested.get();
    }

    /** Whether a processor probe of this instance passed at or after {@code since}. */
    public boolean processorPassedSince(Instant since) {
        Instant pass = lastProcessorPass;
        return pass != null && !pass.isBefore(since);
    }

    /** The probes of a sweep: all three, one at a time; true when every probe passed. */
    public boolean runAll() {
        oneAtATime.lock();
        try {
            boolean passed = true;
            for (ProbeKind kind : ProbeKind.values()) {
                passed &= run(kind);
            }
            storeRequested.set(false);
            scannerRequested.set(false);
            return passed;
        } finally {
            oneAtATime.unlock();
        }
    }

    /** The requested probes and the re-runs of incomplete ones that are due now. */
    public void poll() {
        oneAtATime.lock();
        try {
            Instant now = clock.now();
            for (ProbeKind kind : ProbeKind.values()) {
                boolean rerun = incompleteInARow.getOrDefault(kind, 0) > 0 && elapsed(kind, now, RERUN_AFTER);
                boolean requested = requested(kind) && elapsed(kind, now, MIN_INTERVAL);
                if (rerun || requested) {
                    if (kind == ProbeKind.STORE) {
                        storeRequested.set(false);
                    } else if (kind == ProbeKind.SCANNER) {
                        scannerRequested.set(false);
                    }
                    run(kind);
                }
            }
        } finally {
            oneAtATime.unlock();
        }
    }

    private boolean requested(ProbeKind kind) {
        return switch (kind) {
            case STORE -> storeRequested.get();
            case SCANNER -> scannerRequested.get();
            case PROCESSOR -> false;
        };
    }

    private boolean elapsed(ProbeKind kind, Instant now, Duration interval) {
        Instant last = lastRun.get(kind);
        return last == null || !now.isBefore(last.plus(interval));
    }

    private boolean run(ProbeKind kind) {
        Instant now = clock.now();
        lastRun.put(kind, now);
        ProbeResult result = probe(kind, now);
        switch (result) {
            case PASSED -> {
                incompleteInARow.put(kind, 0);
                if (kind == ProbeKind.PROCESSOR) {
                    // the time it answered, and before the write below, which can fail: it passed all the same
                    lastProcessorPass = clock.now();
                }
                // survey-media spec 5.2: operators read the last passing probe in an incident; only a scanner pass
                // ends a scan block
                health.recordProbePassed(kind == ProbeKind.SCANNER, now);
                return true;
            }
            case WRONG_ANSWER -> {
                incompleteInARow.put(kind, 0);
                log.error("Survey media {} probe answered wrongly; intake paused", kind);
                health.recordFailure(kind.pauseReason(), true, now);
                if (kind == ProbeKind.SCANNER) {
                    health.setScanBlocked(now);
                }
                return false;
            }
            default -> {
                int inARow = incompleteInARow.getOrDefault(kind, 0) + 1;
                incompleteInARow.put(kind, inARow);
                log.warn("Survey media {} probe could not complete ({} in a row)", kind, inARow);
                health.recordFailure(kind.pauseReason(), inARow >= 2, now);
                return false;
            }
        }
    }

    private ProbeResult probe(ProbeKind kind, Instant now) {
        try {
            return switch (kind) {
                case STORE -> {
                    store.probe(Bucket.QUARANTINE);
                    store.probe(Bucket.MEDIA);
                    yield ProbeResult.PASSED;
                }
                case SCANNER -> scanner.probe(now);
                case PROCESSOR -> normalizer.probe();
            };
        } catch (StoreUnavailableException e) {
            return ProbeResult.INCOMPLETE;
        } catch (RuntimeException e) {
            log.warn("Survey media {} probe failed ({})", kind, e.getClass().getSimpleName());
            return ProbeResult.INCOMPLETE;
        }
    }
}
