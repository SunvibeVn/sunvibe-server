package vn.sunvibe.survey.core.application.shared.media;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The intake gate of create, content PUT and commit (survey-media spec A-18, plan PD-4). */
class IntakeGateTest {

    private static final Instant NOW = Instant.parse("2026-11-01T02:00:00Z");

    private final RecordingHealth health = new RecordingHealth();
    private final IntakeGate gate = new IntakeGate(health, () -> NOW);

    @Test
    void open_passes() {
        health.row = new IntakeHealth(4, 2, NOW.minusSeconds(60), NOW, null, null, null, null, null, null);

        assertDoesNotThrow(gate::requireOpen);
        assertEquals(List.of("read"), health.calls);
    }

    @Test
    void paused_answersIntakePaused() {
        health.row = new IntakeHealth(4, 3, NOW.minusSeconds(60), NOW, NOW, null, null, null, NOW,
                PauseReason.STORE);

        MediaIntakePausedException paused = assertThrows(MediaIntakePausedException.class, gate::requireOpen);

        assertFalse(paused.sweepStale());
        gate.recordRefusal(paused);
        assertEquals(List.of("read"), health.calls);
    }

    @Test
    void staleSweep_answersPaused() {
        for (Instant lastSweep : new Instant[] {null, NOW.minus(Duration.ofMinutes(10)).minusMillis(1)}) {
            health.calls.clear();
            health.row = new IntakeHealth(4, 2, lastSweep, NOW, null, null, null, null, null, null);

            MediaIntakePausedException paused = assertThrows(MediaIntakePausedException.class, gate::requireOpen);

            assertTrue(paused.sweepStale());
            assertEquals(List.of("read"), health.calls, "nothing is written inside the caller's transaction");
            gate.recordRefusal(paused);
            assertEquals(List.of("read", "failure:SWEEP_STALE:pause:" + NOW), health.calls);
        }
    }

    @Test
    void sweepTenMinutesOld_stillOpen() {
        health.row = new IntakeHealth(4, 2, NOW.minus(Duration.ofMinutes(10)), NOW, null, null, null, null, null,
                null);

        assertDoesNotThrow(gate::requireOpen);
    }

    /** A health row double that journals reads and writes. */
    private static final class RecordingHealth implements IntakeHealthRepository {

        private IntakeHealth row;
        private final List<String> calls = new ArrayList<>();

        @Override
        public IntakeHealth read() {
            calls.add("read");
            return row;
        }

        @Override
        public void recordFailure(PauseReason reason, boolean pause, Instant appNow) {
            calls.add("failure:" + reason + (pause ? ":pause:" : ":") + appNow);
        }

        @Override
        public void setScanBlocked(Instant appNow) {
            calls.add("scanBlocked");
        }

        @Override
        public void recordProbePassed(boolean clearScanBlock, Instant appNow) {
            calls.add("probePassed");
        }

        @Override
        public boolean resumeIf(long seqAtStart, Instant appNow) {
            calls.add("resume");
            return false;
        }

        @Override
        public void recordSweepCompleted(Instant appNow) {
            calls.add("sweep");
        }

        @Override
        public void beatWorker(Instant appNow) {
            calls.add("beatWorker");
        }

        @Override
        public void beatDeletion(Instant appNow) {
            calls.add("beatDeletion");
        }
    }
}
