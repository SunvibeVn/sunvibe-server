package vn.sunvibe.survey.core.domain.model.media;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure rules of the intake health row (survey-media spec A-18). */
class IntakeHealthTest {

    private static final Instant T0 = Instant.parse("2026-11-01T02:00:00Z");

    private static IntakeHealth open() {
        return new IntakeHealth(3, 2, T0, T0, null, null, null, null, null, null);
    }

    private static IntakeHealth paused(PauseReason reason) {
        return new IntakeHealth(3, 3, T0, T0, T0, null, null, null, T0, reason);
    }

    @Test
    void seeded_isPausedNotStarted() {
        IntakeHealth seed = IntakeHealth.seed(T0);

        assertTrue(seed.paused());
        assertEquals(PauseReason.NOT_STARTED, seed.pauseReason());
        assertNull(seed.lastSweepOkAt());
    }

    @Test
    void failure_withoutPause_bumpsSeqOnly() {
        IntakeHealth after = open().failure(null, T0.plusSeconds(10));

        assertEquals(4, after.failureSeq());
        assertEquals(T0.plusSeconds(10), after.lastFailureAt());
        assertFalse(after.paused());
        assertEquals(2, after.stateEpoch());
    }

    @Test
    void failure_withPause_pausesOnceKeepingTheFirstReasonAndTime() {
        IntakeHealth first = open().failure(PauseReason.STORE, T0.plusSeconds(10));
        IntakeHealth second = first.failure(PauseReason.SCANNER, T0.plusSeconds(20));

        assertTrue(second.paused());
        assertEquals(PauseReason.STORE, second.pauseReason());
        assertEquals(T0.plusSeconds(10), second.intakePausedSince());
        assertEquals(5, second.failureSeq());
    }

    @Test
    void resume_failureSeqChanged_isRefused() {
        IntakeHealth health = paused(PauseReason.STORE);
        long seqAtStart = health.failureSeq();
        IntakeHealth failedDuringSweep = health.failure(null, T0.plusSeconds(30));

        assertSame(failedDuringSweep, failedDuringSweep.resumed(seqAtStart, T0.plusSeconds(60)));
        assertTrue(failedDuringSweep.paused());
    }

    @Test
    void resume_notPaused_changesNothing() {
        IntakeHealth health = open();

        assertSame(health, health.resumed(health.failureSeq(), T0.plusSeconds(60)));
    }

    @Test
    void resume_pausedWithSameSeq_opensAndRecordsTheSweep() {
        IntakeHealth health = paused(PauseReason.NOT_STARTED);

        IntakeHealth resumed = health.resumed(health.failureSeq(), T0.plusSeconds(60));

        assertFalse(resumed.paused());
        assertNull(resumed.pauseReason());
        assertEquals(T0.plusSeconds(60), resumed.lastSweepOkAt());
        assertEquals(health.stateEpoch() + 1, resumed.stateEpoch());
    }

    @Test
    void stateEpoch_movesOnlyOnRealPauseOrResume() {
        IntakeHealth health = open();
        long epoch = health.stateEpoch();

        health = health.failure(null, T0.plusSeconds(1));
        assertEquals(epoch, health.stateEpoch());
        health = health.failure(PauseReason.PROCESSOR, T0.plusSeconds(2));
        assertEquals(epoch + 1, health.stateEpoch());
        health = health.failure(PauseReason.SCANNER, T0.plusSeconds(3));
        assertEquals(epoch + 1, health.stateEpoch());
        health = health.resumed(health.failureSeq(), T0.plusSeconds(4));
        assertEquals(epoch + 2, health.stateEpoch());
        health = health.resumed(health.failureSeq(), T0.plusSeconds(5));
        assertEquals(epoch + 2, health.stateEpoch());
    }

    @Test
    void sweepStale_whenNeverSweptOrOlderThanTenMinutes() {
        assertTrue(IntakeHealth.seed(T0).sweepStale(T0));
        assertFalse(open().sweepStale(T0.plus(Duration.ofMinutes(10))));
        assertTrue(open().sweepStale(T0.plus(Duration.ofMinutes(10)).plusMillis(1)));
    }

    @Test
    void constructor_pauseColumnsHalfSet_isRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new IntakeHealth(0, 0, null, null, null, null, null, null, T0, null));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new IntakeHealth(0, 0, null, null, null, null, null, null, null, PauseReason.STORE));
    }
}
