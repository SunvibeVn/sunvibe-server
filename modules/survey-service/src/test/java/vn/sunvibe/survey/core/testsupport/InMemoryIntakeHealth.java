package vn.sunvibe.survey.core.testsupport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;

/** The intake health row in memory (survey-media spec A-18); every call is recorded. */
public class InMemoryIntakeHealth implements IntakeHealthRepository {

    public IntakeHealth row;
    public final List<String> calls = new ArrayList<>();

    /** Open: no pause, a complete sweep at {@code now}. */
    public InMemoryIntakeHealth open(Instant now) {
        row = new IntakeHealth(0, 1, now, now, null, null, null, null, null, null);
        return this;
    }

    /** Paused for the reason since {@code now}. */
    public void pause(PauseReason reason, Instant now) {
        row = new IntakeHealth(1, 2, now, now, now, null, null, null, now, reason);
    }

    /** Open, but the last complete sweep at {@code lastSweep}. */
    public void sweptAt(Instant lastSweep) {
        row = new IntakeHealth(0, 1, lastSweep, lastSweep, null, null, null, null, null, null);
    }

    @Override
    public IntakeHealth read() {
        calls.add("read");
        return row;
    }

    /** spec A-18: one more failure; a pause keeps the first pause time and reason. */
    @Override
    public void recordFailure(PauseReason reason, boolean pause, Instant appNow) {
        calls.add("failure:" + reason + (pause ? ":pause" : ""));
        if (row == null) {
            return;
        }
        boolean opening = pause && row.intakePausedSince() == null;
        row = new IntakeHealth(row.failureSeq() + 1, row.stateEpoch() + (opening ? 1 : 0), row.lastSweepOkAt(),
                row.lastProbeOkAt(), appNow, row.lastWorkerBeatAt(), row.lastDeletionBeatAt(), row.scanBlockedSince(),
                pause && row.intakePausedSince() == null ? appNow : row.intakePausedSince(),
                pause && row.pauseReason() == null ? reason : row.pauseReason());
    }

    @Override
    public void setScanBlocked(Instant appNow) {
        calls.add("scanBlocked");
        if (row != null && row.scanBlockedSince() == null) {
            row = new IntakeHealth(row.failureSeq(), row.stateEpoch(), row.lastSweepOkAt(), row.lastProbeOkAt(),
                    row.lastFailureAt(), row.lastWorkerBeatAt(), row.lastDeletionBeatAt(), appNow,
                    row.intakePausedSince(), row.pauseReason());
        }
    }

    @Override
    public void clearScanBlocked() {
        calls.add("scanUnblocked");
        if (row != null) {
            row = new IntakeHealth(row.failureSeq(), row.stateEpoch(), row.lastSweepOkAt(), row.lastProbeOkAt(),
                    row.lastFailureAt(), row.lastWorkerBeatAt(), row.lastDeletionBeatAt(), null,
                    row.intakePausedSince(), row.pauseReason());
        }
    }

    /** spec A-18: only while paused and with the sequence read before the sweep's probes. */
    @Override
    public boolean resumeIf(long seqAtStart, Instant appNow) {
        calls.add("resume");
        if (row == null || row.failureSeq() != seqAtStart || row.intakePausedSince() == null) {
            return false;
        }
        row = new IntakeHealth(row.failureSeq(), row.stateEpoch() + 1, appNow, row.lastProbeOkAt(),
                row.lastFailureAt(), row.lastWorkerBeatAt(), row.lastDeletionBeatAt(), row.scanBlockedSince(), null,
                null);
        calls.add("resumed");
        return true;
    }

    @Override
    public void recordSweepCompleted(Instant appNow) {
        calls.add("swept");
        if (row != null) {
            row = new IntakeHealth(row.failureSeq(), row.stateEpoch(), appNow, row.lastProbeOkAt(),
                    row.lastFailureAt(), row.lastWorkerBeatAt(), row.lastDeletionBeatAt(), row.scanBlockedSince(),
                    row.intakePausedSince(), row.pauseReason());
        }
    }

    @Override
    public void beatWorker(Instant appNow) {
        calls.add("workerBeat");
        if (row != null) {
            row = new IntakeHealth(row.failureSeq(), row.stateEpoch(), row.lastSweepOkAt(), row.lastProbeOkAt(),
                    row.lastFailureAt(), appNow, row.lastDeletionBeatAt(), row.scanBlockedSince(),
                    row.intakePausedSince(), row.pauseReason());
        }
    }

    @Override
    public void beatDeletion(Instant appNow) {
        calls.add("deletionBeat");
        if (row != null) {
            row = new IntakeHealth(row.failureSeq(), row.stateEpoch(), row.lastSweepOkAt(), row.lastProbeOkAt(),
                    row.lastFailureAt(), row.lastWorkerBeatAt(), appNow, row.scanBlockedSince(),
                    row.intakePausedSince(), row.pauseReason());
        }
    }
}
