package vn.sunvibe.survey.core.application.service.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.survey.core.application.shared.media.MediaWorkCounters;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.application.shared.external.media.ProcessorUnavailableException;
import vn.sunvibe.survey.core.application.shared.external.media.ScannerSilentException;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.policy.media.ReadBudgetArithmetic;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;
import vn.sunvibe.survey.core.testsupport.InMemoryBodySpool;
import vn.sunvibe.survey.core.testsupport.MediaFixture;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;

/**
 * The processing worker (survey-media spec 4.4 step 3, 4.5, A-7, A-27, A-28; plan Task A15): one claimed job per run,
 * re-hash, format, scan, normalize, create-only variant writes and an outcome commit fenced by the lease token. Fakes
 * for the store, scanner, processor and a clock the test moves.
 */
class MediaProcessingWorkerTest {

    private static final byte[] SOURCE = "a jpeg source".getBytes(StandardCharsets.UTF_8);
    private static final String SHA = InMemoryBodySpool.sha256(SOURCE);
    private static final String SALE_A = MediaServiceHarness.SALE_A;

    private final MediaServiceHarness h = new MediaServiceHarness();
    private final String checklist = h.checklist(ChecklistServiceHarness.LEAD);
    /** A minute into the current clock hour, so the hourly counters of a test stay in one hour. */
    private Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusSeconds(60);
    private final ScriptedScanner scanner = new ScriptedScanner();
    private final ScriptedNormalizer normalizer = new ScriptedNormalizer();
    private final MediaProcessingWorker worker = new MediaProcessingWorker(h.jobs, h.media, h.sets, h.variants,
            h.base.checklists, h.store, scanner, normalizer, h.health, h.probes,
            new MediaWorkCounters(h.counters, h.transactions, () -> now), h.recorder, () -> now,
            () -> com.github.f4b6a3.ulid.UlidCreator.getMonotonicUlid().toString(), h.transactions);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger workerLog = (Logger) LoggerFactory.getLogger(MediaProcessingWorker.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        workerLog.addAppender(logs);
        h.health.open(now);
    }

    @AfterEach
    void releaseLogs() {
        workerLog.detachAppender(logs);
    }

    // ==================== outcomes ====================

    @Test
    void clean_readyWithThreeVariants_sourceDeletedAfterTheCommit() {
        SurveyMedia photo = processing();

        assertTrue(worker.runOnce());

        SurveyMedia ready = stored(photo);
        assertEquals(MediaStatus.READY, ready.getStatus());
        assertEquals(3, ready.getVariants().size());
        assertEquals(SourceFormat.JPEG, ready.getSourceFormat());
        assertTrue(h.store.objects.containsKey("MEDIA:media/" + photo.getId() + "/1/FULL"));
        assertFalse(h.store.objects.containsKey("QUARANTINE:" + photo.getSourceStorageKey()), "the source is gone");
        assertTrue(ready.getSourceDeletedAt() != null);
        assertEquals(MediaJobState.DONE, job(photo).state());
        assertFalse(worker.runOnce(), "nothing left");
    }

    @Test
    void variantsCarryProcessorVersion() {
        SurveyMedia photo = processing();

        worker.runOnce();

        assertTrue(stored(photo).getVariants().stream().allMatch(v -> v.processorVersion().equals("test-9")));
    }

    @Test
    void found_rejectedMalwareOneEventIdsOnly() {
        SurveyMedia photo = processing();
        scanner.next(() -> MalwareScanner.ScanVerdict.FOUND);

        worker.runOnce();

        SurveyMedia rejected = stored(photo);
        assertEquals(MediaStatus.REJECTED, rejected.getStatus());
        assertEquals(MediaStatusReason.MALWARE_DETECTED, rejected.getStatusReason());
        assertEquals(1, h.base.securityEvents.all().size());
        var event = h.base.securityEvents.all().get(0);
        assertEquals(SecurityAction.SCAN, event.action());
        assertEquals(SecurityOutcome.MALWARE_DETECTED, event.outcome());
        assertEquals(SALE_A, event.userId());
        assertEquals(String.valueOf(job(photo).id()), event.correlationIdOrNull());
        assertTrue(logs.list.stream().noneMatch(line -> line.getFormattedMessage().contains(SHA)));
        assertEquals(0, normalizer.normalized, "nothing is decoded after a FOUND");
    }

    @Test
    void limitAnswer_retriedTwiceThenScanLimitExceeded_noEvent() {
        SurveyMedia photo = processing();
        for (int i = 0; i < 3; i++) {
            scanner.next(() -> MalwareScanner.ScanVerdict.perFileCondition("HEURISTICS_LIMITS_EXCEEDED"));
        }

        worker.runOnce();
        assertEquals(MediaStatus.PROCESSING, stored(photo).getStatus());
        assertEquals(1, job(photo).limitHits());
        later();
        worker.runOnce();
        assertEquals(MediaStatus.PROCESSING, stored(photo).getStatus());
        later();
        worker.runOnce();

        assertEquals(MediaStatusReason.SCAN_LIMIT_EXCEEDED, stored(photo).getStatusReason());
        assertEquals(List.of(), h.base.securityEvents.all());
        assertEquals(3, expensive(), "every scan-limit result counts as expensive (spec A-27)");
    }

    @Test
    void otherErrorLine_isPerFileCondition() {
        SurveyMedia photo = processing();
        scanner.next(() -> MalwareScanner.ScanVerdict.perFileCondition("SCAN_ERROR_LINE"));

        worker.runOnce();

        assertEquals(1, job(photo).limitHits());
        assertEquals(MediaStatus.PROCESSING, stored(photo).getStatus());
        assertEquals(0, h.probes.scanner, "an answer is no silence");
    }

    @Test
    void unsupportedFormat_decidedBeforeScan_noScanCall() {
        SurveyMedia photo = processing();
        normalizer.format = Optional.empty();

        worker.runOnce();

        assertEquals(MediaStatusReason.UNSUPPORTED_FORMAT, stored(photo).getStatusReason());
        assertEquals(0, scanner.calls);
    }

    @Test
    void scannerSilent_infrastructureRetryAndScannerProbe_neverScanLimitNeverExpensive() {
        SurveyMedia photo = processing();
        scanner.next(() -> {
            throw new ScannerSilentException();
        });

        worker.runOnce();

        MediaJob job = job(photo);
        assertEquals(MediaJobState.PENDING, job.state());
        assertEquals(0, job.limitHits());
        assertEquals(now.plusSeconds(30), job.dueAt(), "attempt 2 after 30 s (spec A-7)");
        assertEquals(1, h.probes.scanner);
        assertEquals(0, expensive());
    }

    /** SV-122: a retry the fence refuses changes no row, says so in the log and does not claim a retry. */
    @Test
    void retryRefusedByTheFence_logsItAndNotARetry() {
        SurveyMedia photo = processing();
        scanner.next(() -> {
            h.jobs.stealLease(job(photo).id(), "01JOTHERWORKERLEASE0000001");
            throw new ScannerSilentException();
        });

        worker.runOnce();

        MediaJob job = job(photo);
        assertEquals("01JOTHERWORKERLEASE0000001", job.leaseTokenOrNull(), "the other worker's lease is untouched");
        assertEquals(1, messages("retry was not applied").size());
        assertTrue(messages("retries after attempt").isEmpty());
    }

    /** SV-122: a defer the fence refuses changes no row and says so in the log. */
    @Test
    void deferRefusedByTheFence_logsIt() {
        SurveyMedia photo = processing();
        h.health.row = scanBlockedHealth();
        normalizer.beforeDetect = () -> h.jobs.stealLease(job(photo).id(), "01JOTHERWORKERLEASE0000001");

        worker.runOnce();

        assertEquals("01JOTHERWORKERLEASE0000001", job(photo).leaseTokenOrNull());
        assertEquals(1, messages("defer was not applied").size());
    }

    /** SV-122 pin: an applied retry is logged as a retry. */
    @Test
    void retryApplied_logsTheRetry() {
        SurveyMedia photo = processing();
        scanner.next(() -> {
            throw new ScannerSilentException();
        });

        worker.runOnce();

        assertEquals(1, messages("retries after attempt").size());
        assertTrue(messages("retry was not applied").isEmpty());
    }

    @Test
    void backWithinWindow_ready() {
        SurveyMedia photo = processing();
        scanner.next(() -> {
            throw new ScannerSilentException();
        });
        worker.runOnce();
        assertFalse(worker.runOnce(), "not due before its backoff");

        later();
        worker.runOnce();

        assertEquals(MediaStatus.READY, stored(photo).getStatus());
        assertTrue(h.store.objects.containsKey("MEDIA:media/" + photo.getId() + "/2/FULL"), "attempt 2's keys");
    }

    @Test
    void past50Minutes_failedSourceGone() {
        SurveyMedia photo = processing();
        now = now.plus(Duration.ofMinutes(51));

        worker.runOnce();

        assertEquals(MediaStatus.FAILED, stored(photo).getStatus());
        assertFalse(h.store.objects.containsKey("QUARANTINE:" + photo.getSourceStorageKey()));
        assertEquals(0, scanner.calls);
        assertEquals(MediaJobState.DONE, job(photo).state());
    }

    @Test
    void missingSource_failsAtOnce() {
        SurveyMedia photo = processing();
        h.store.objects.remove("QUARANTINE:" + photo.getSourceStorageKey());

        worker.runOnce();

        assertEquals(MediaStatus.FAILED, stored(photo).getStatus());
        assertEquals(MediaJobState.DONE, job(photo).state());
    }

    @Test
    void sourceHashMismatch_failsClosed() {
        SurveyMedia photo = processing();
        h.store.objects.put("QUARANTINE:" + photo.getSourceStorageKey(), "other bytes".getBytes(StandardCharsets.UTF_8));

        worker.runOnce();

        assertEquals(MediaStatus.FAILED, stored(photo).getStatus());
        assertEquals(0, scanner.calls);
    }

    @Test
    void limitHitTwice_rejectedUnreadable() {
        SurveyMedia photo = processing();
        normalizer.next(ImageNormalizer.NormalizeResult.LimitHit::new);
        normalizer.next(ImageNormalizer.NormalizeResult.LimitHit::new);

        worker.runOnce();
        assertEquals(MediaStatus.PROCESSING, stored(photo).getStatus());
        later();
        worker.runOnce();

        assertEquals(MediaStatusReason.UNREADABLE, stored(photo).getStatusReason());
        assertEquals(2, expensive());
    }

    @Test
    void sixFailedAttempts_failedProcessingUnavailableJobDone() {
        SurveyMedia photo = processing();
        for (int i = 0; i < 6; i++) {
            normalizer.next(() -> {
                throw new ProcessorUnavailableException("PROCESSOR_REFUSED");
            });
        }
        Duration[] backoff = {Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2),
                Duration.ofMinutes(5), Duration.ofMinutes(10)};

        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.runOnce();
            assertEquals(now.plus(backoff[attempt - 1]), job(photo).dueAt(), "after attempt " + attempt);
            now = job(photo).dueAt();
        }
        worker.runOnce();

        assertEquals(MediaStatusReason.PROCESSING_UNAVAILABLE, stored(photo).getStatusReason());
        assertEquals(MediaJobState.DONE, job(photo).state());
        assertEquals("PROCESSOR_REFUSED", job(photo).lastErrorCodeOrNull());
    }

    @Test
    void removedBeforeWork_cancelledRemovedObjectsDeleted() {
        SurveyMedia photo = processing();
        SurveyMedia removed = stored(photo);
        removed.remove(RemovalKind.HOLDER, SALE_A, null, null, now);
        h.media.put(removed);

        worker.runOnce();

        SurveyMedia cancelled = stored(photo);
        assertEquals(MediaStatus.CANCELLED, cancelled.getStatus());
        assertEquals(MediaStatusReason.REMOVED, cancelled.getStatusReason());
        assertFalse(h.store.objects.containsKey("QUARANTINE:" + photo.getSourceStorageKey()));
        assertEquals(0, scanner.calls);
    }

    @Test
    void removedAtOutcome_neverReady() {
        SurveyMedia photo = processing();
        normalizer.beforeAnswer = () -> {
            SurveyMedia removed = stored(photo);
            removed.remove(RemovalKind.HOLDER, SALE_A, null, null, now);
            h.media.put(removed);
        };

        worker.runOnce();

        assertEquals(MediaStatus.CANCELLED, stored(photo).getStatus());
        assertTrue(h.store.objects.keySet().stream().noneMatch(key -> key.startsWith("MEDIA:")),
                "its variants are deleted");
    }

    @Test
    void lostLease_outcomeNotCommitted_ownObjectsDeleted() {
        SurveyMedia photo = processing();
        normalizer.beforeAnswer = () -> h.jobs.stealLease(job(photo).id(), "01JOTHERWORKERLEASE0000001");

        worker.runOnce();

        assertEquals(MediaStatus.PROCESSING, stored(photo).getStatus(), "a fenced-out worker commits nothing");
        assertTrue(h.store.objects.keySet().stream().noneMatch(key -> key.startsWith("MEDIA:")));
        assertTrue(h.store.objects.containsKey("QUARANTINE:" + photo.getSourceStorageKey()), "the source stays");
    }

    @Test
    void scanBlocked_scansNothing() {
        SurveyMedia photo = processing();
        h.health.row = scanBlockedHealth();

        worker.runOnce();

        assertEquals(0, scanner.calls);
        MediaJob job = job(photo);
        assertEquals(MediaJobState.PENDING, job.state());
        assertEquals(0, job.attempts(), "a blocked scan is not an attempt");
        assertEquals(MediaStatus.PROCESSING, stored(photo).getStatus());
    }

    @Test
    void oversizedVariant_failedNothingStored() {
        SurveyMedia photo = processing();
        normalizer.next(() -> new ImageNormalizer.NormalizeResult.Failed("FULL_OVER_CAP"));

        worker.runOnce();

        assertEquals(MediaStatus.FAILED, stored(photo).getStatus());
        assertTrue(h.store.objects.keySet().stream().noneMatch(key -> key.startsWith("MEDIA:")));
    }

    @Test
    void workerBeat_recordedEachRun() {
        processing();

        worker.runOnce();
        worker.runOnce();

        assertEquals(2, h.health.calls.stream().filter("workerBeat"::equals).count());
    }

    // ==================== helpers ====================

    /** A committed photo of SALE_A with its source in quarantine and a due PROCESS job. */
    private SurveyMedia processing() {
        h.sets.insertIgnoringExisting(checklist, now);
        SurveyMedia photo = SurveyMedia.create(MediaFixture.newId(), checklist, MediaServiceHarness.newKey(), 1,
                MediaPlacement.asset(), EvidenceCategory.EXTERIOR_ACCESS, null, SALE_A, SOURCE.length, SHA, now);
        String key = "quarantine/" + photo.getId() + "/01JATTEMPT0000000000000001";
        photo.claim(MediaFixture.TOKEN, key, now);
        photo.commit(key, SOURCE.length, now);
        h.media.put(photo);
        h.store.objects.put("QUARANTINE:" + key, SOURCE.clone());
        h.jobs.upsertProcess(photo.getId(), now, now);
        h.jobs.uploaders.put(photo.getId(), SALE_A);
        return h.media.stored(photo.getId()).orElseThrow();
    }

    private SurveyMedia stored(SurveyMedia photo) {
        return h.media.stored(photo.getId()).orElseThrow();
    }

    private MediaJob job(SurveyMedia photo) {
        return h.jobs.findByMedia(photo.getId()).get(0);
    }

    private IntakeHealth scanBlockedHealth() {
        IntakeHealth row = h.health.row;
        return new IntakeHealth(row.failureSeq(), row.stateEpoch(), row.lastSweepOkAt(), row.lastProbeOkAt(),
                now, null, null, now, now, vn.sunvibe.survey.core.domain.model.media.PauseReason.SCANNER);
    }

    private List<String> messages(String part) {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(line -> line.contains(part)).toList();
    }

    private void later() {
        now = now.plus(Duration.ofMinutes(11));
    }

    private long expensive() {
        return h.counters.counterValue(SALE_A, ReadBudgetArithmetic.hourStart(now), MediaCounterKind.EXPENSIVE);
    }

    /** Answers in order, then CLEAN. */
    private static final class ScriptedScanner implements MalwareScanner {

        private final Deque<Supplier<ScanVerdict>> answers = new ArrayDeque<>();
        int calls;

        void next(Supplier<ScanVerdict> answer) {
            answers.add(answer);
        }

        @Override
        public ScanVerdict scan(InputStream content) {
            calls++;
            return answers.isEmpty() ? ScanVerdict.CLEAN : answers.poll().get();
        }

        @Override
        public ProbeResult probe(Instant at) {
            return ProbeResult.PASSED;
        }
    }

    /** Detects JPEG and normalizes into three small variants unless scripted. */
    private static final class ScriptedNormalizer implements ImageNormalizer {

        private final Deque<Supplier<NormalizeResult>> answers = new ArrayDeque<>();
        Optional<SourceFormat> format = Optional.of(SourceFormat.JPEG);
        Runnable beforeAnswer = () -> { };
        Runnable beforeDetect = () -> { };
        int normalized;

        void next(Supplier<NormalizeResult> answer) {
            answers.add(answer);
        }

        @Override
        public Optional<SourceFormat> detect(InputStream source, long length) {
            beforeDetect.run();
            return format;
        }

        @Override
        public NormalizeResult normalize(InputStream source, long length) {
            normalized++;
            beforeAnswer.run();
            if (!answers.isEmpty()) {
                return answers.poll().get();
            }
            return new NormalizeResult.Normalized(4000, 3000, List.of(
                    new NormalizedVariant(VariantKind.FULL, 4000, 3000, new byte[] {1, 2, 3}),
                    new NormalizedVariant(VariantKind.PREVIEW, 1600, 1200, new byte[] {4, 5}),
                    new NormalizedVariant(VariantKind.THUMBNAIL, 400, 300, new byte[] {6})));
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
