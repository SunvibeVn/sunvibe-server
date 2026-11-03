package vn.sunvibe.survey.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.testsupport.InMemoryIntakeHealth;
import vn.sunvibe.survey.core.testsupport.InMemoryMediaStore;

/**
 * The health probes (survey-media spec A-18; SM-3 probe part; plan Task A21): a wrong answer pauses at once (a wrong
 * scanner answer also blocks every scan), an incomplete probe pauses only on its second in a row, probes run one at a
 * time and at most once a minute per kind besides that re-run. The scanner adapter maps each scanner case to its
 * result (ClamAvScannerTest.probe_*); here the runner's reaction to that result is checked.
 */
class MediaProbeRunnerTest {

    private static final Instant T0 = Instant.parse("2026-11-03T02:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final InMemoryIntakeHealth health = new InMemoryIntakeHealth().open(T0);
    private final InMemoryMediaStore store = new InMemoryMediaStore(new ArrayList<>());
    private final FakeScanner scanner = new FakeScanner();
    private final FakeNormalizer normalizer = new FakeNormalizer();
    private final MediaProbeRunner runner = new MediaProbeRunner(store, scanner, normalizer, health, now::get);

    @Test
    void eicarAnsweredOk_pausesAndBlocksScan() {
        scanner.result = ProbeResult.WRONG_ANSWER;

        assertFalse(runner.runAll());

        assertTrue(health.calls.contains("failure:SCANNER:pause"), health.calls.toString());
        assertNotNull(health.row.scanBlockedSince());
        assertNotNull(health.row.intakePausedSince());
    }

    @Test
    void eicarFoundOnlyAsLimit_pauses() {
        scanner.result = ProbeResult.WRONG_ANSWER;

        assertFalse(runner.runAll());

        assertNotNull(health.row.intakePausedSince());
    }

    @Test
    void cleanSampleNotOk_pauses() {
        scanner.result = ProbeResult.WRONG_ANSWER;

        assertFalse(runner.runAll());

        assertNotNull(health.row.intakePausedSince());
    }

    @Test
    void staleSignatures_pauses() {
        scanner.result = ProbeResult.WRONG_ANSWER;

        assertFalse(runner.runAll());

        assertNotNull(health.row.intakePausedSince());
    }

    @Test
    void incompleteOnce_bumpsSeqNoPause_secondPauses() {
        scanner.result = ProbeResult.INCOMPLETE;

        assertFalse(runner.runAll());
        assertEquals(1, health.row.failureSeq());
        assertNull(health.row.intakePausedSince(), "the first incomplete probe does not pause");

        now.set(T0.plusSeconds(30));
        runner.poll();
        assertEquals(1, scanner.probes.get(), "the re-run waits at least 30 seconds");

        now.set(T0.plusSeconds(45));
        runner.poll();
        assertEquals(2, scanner.probes.get(), "re-run within one minute");
        assertNotNull(health.row.intakePausedSince(), "the second in a row pauses");
        assertNull(health.row.scanBlockedSince(), "no answer is not a wrong answer");
    }

    @Test
    void secondInstanceWorker_scansNothingUntilPassingScannerProbe_intakeResumeAloneDoesNot() {
        scanner.result = ProbeResult.WRONG_ANSWER;
        runner.runAll();
        long seq = health.row.failureSeq();

        assertTrue(health.resumeIf(seq, T0.plusSeconds(60)));
        assertNotNull(health.row.scanBlockedSince(), "the resume of intake keeps the scan block");

        scanner.result = ProbeResult.PASSED;
        now.set(T0.plusSeconds(120));
        assertTrue(runner.runAll());
        assertNull(health.row.scanBlockedSince(), "only a passing scanner probe clears it");
    }

    @Test
    void eicarProbe_noEventNoSignatureName() {
        Logger logger = (Logger) LoggerFactory.getLogger(MediaProbeRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            scanner.result = ProbeResult.WRONG_ANSWER;
            runner.runAll();
            scanner.result = ProbeResult.INCOMPLETE;
            now.set(T0.plusSeconds(120));
            runner.runAll();
        } finally {
            logger.detachAppender(appender);
        }

        for (ILoggingEvent event : appender.list) {
            String text = event.getFormattedMessage().toLowerCase(java.util.Locale.ROOT);
            assertFalse(text.contains("eicar") || text.contains("signature"), text);
        }
        assertTrue(health.calls.stream().noneMatch(call -> call.startsWith("event")));
    }

    @Test
    void probes_oneAtATimeAtMostOncePerMinute() throws Exception {
        runner.requestStoreProbe();
        runner.requestStoreProbe();
        runner.poll();
        assertEquals(2, store.probes.size(), "one store probe: both buckets");

        now.set(T0.plusSeconds(30));
        runner.requestStoreProbe();
        runner.poll();
        assertEquals(2, store.probes.size(), "at most once a minute");

        now.set(T0.plusSeconds(61));
        runner.poll();
        assertEquals(4, store.probes.size(), "the pending request runs once the minute is over");

        normalizer.slow = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread thread = new Thread(runner::runAll);
            threads.add(thread);
            thread.start();
        }
        Thread.sleep(200);
        normalizer.slow.countDown();
        for (Thread thread : threads) {
            thread.join(5_000);
        }
        assertEquals(1, normalizer.maxInFlight.get(), "one probe at a time");
    }

    @Test
    void notFound412OrClientAbort_requestNoProbe() {
        runner.poll();

        assertEquals(0, store.probes.size());
        assertEquals(0, scanner.probes.get());
        assertEquals(0, normalizer.probes.get());
    }

    /**
     * spec 5.2, A-18 (code review M3, SV-069): a passing probe records its time for the operators and changes no pause;
     * a passing store probe keeps the scan block, only a passing scanner probe clears it.
     */
    @Test
    void passingProbe_recordsProbeTimeKeepsPause_storePassKeepsScanBlock() {
        scanner.result = ProbeResult.WRONG_ANSWER;
        now.set(T0.plusSeconds(10));
        runner.runAll();
        scanner.result = ProbeResult.PASSED;

        now.set(T0.plusSeconds(90));
        runner.requestStoreProbe();
        runner.poll();

        assertEquals(T0.plusSeconds(90), health.row.lastProbeOkAt());
        assertNotNull(health.row.intakePausedSince(), "a passing probe never resumes intake (only a sweep does)");
        assertNotNull(health.row.scanBlockedSince(), "a passing store probe keeps the scan block");

        store.failProbe = true;
        now.set(T0.plusSeconds(160));
        runner.requestStoreProbe();
        runner.poll();
        store.failProbe = false;

        assertEquals(T0.plusSeconds(90), health.row.lastProbeOkAt(), "a failing probe records no probe time");

        now.set(T0.plusSeconds(200));
        runner.requestScannerProbe();
        runner.poll();

        assertEquals(T0.plusSeconds(200), health.row.lastProbeOkAt());
        assertNull(health.row.scanBlockedSince());
        assertNotNull(health.row.intakePausedSince());
    }

    @Test
    void allPassing_runAllTrue_noFailure() {
        assertTrue(runner.runAll());

        assertEquals(0, health.row.failureSeq());
        assertEquals(List.of(), health.calls.stream().filter(call -> call.startsWith("failure")).toList());
    }

    // ==================== fakes ====================

    private static final class FakeScanner implements MalwareScanner {
        volatile ProbeResult result = ProbeResult.PASSED;
        final AtomicInteger probes = new AtomicInteger();

        @Override
        public ScanVerdict scan(InputStream content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProbeResult probe(Instant at) {
            probes.incrementAndGet();
            return result;
        }
    }

    private static final class FakeNormalizer implements ImageNormalizer {
        final AtomicInteger probes = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        volatile CountDownLatch slow;

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
            probes.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                if (slow != null) {
                    slow.await(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return ProbeResult.PASSED;
        }

        @Override
        public String processorVersion() {
            return "test-9";
        }
    }
}
