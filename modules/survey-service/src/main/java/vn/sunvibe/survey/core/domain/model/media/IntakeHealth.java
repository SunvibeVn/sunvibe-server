package vn.sunvibe.survey.core.domain.model.media;

import java.time.Duration;
import java.time.Instant;

/**
 * The intake health row shared by every instance (survey-media spec 5.2, A-18) and its pure rules, which the JDBC
 * adapter mirrors in SQL. Every failure bumps {@code failureSeq}; a pause bumps {@code stateEpoch} only when intake
 * was open; a resume needs the {@code failureSeq} read before the sweep's probes, so a failure during the sweep is
 * never overwritten. {@code scanBlockedSince} is set by a wrong scanner answer and cleared only by a passing scanner
 * probe, independently of the pause. The probe verdicts are applied by MediaProbeRunner through the repository.
 */
public record IntakeHealth(long failureSeq, long stateEpoch, Instant lastSweepOkAt, Instant lastProbeOkAt,
                           Instant lastFailureAt, Instant lastWorkerBeatAt, Instant lastDeletionBeatAt,
                           Instant scanBlockedSince, Instant intakePausedSince, PauseReason pauseReason) {

    /** survey-media spec A-18: intake pauses when the last complete sweep is older than this (the sweeper runs every 5 minutes). */
    public static final Duration SWEEP_STALE_AFTER = Duration.ofMinutes(10);

    public IntakeHealth {
        if ((intakePausedSince == null) != (pauseReason == null)) {
            throw new IllegalArgumentException("A pause has its time and reason together.");
        }
    }

    /** The row the migration seeds: paused NOT_STARTED, so intake opens only after the first complete sweep. */
    public static IntakeHealth seed(Instant now) {
        return new IntakeHealth(0, 0, null, null, null, null, null, null, now, PauseReason.NOT_STARTED);
    }

    public boolean paused() {
        return intakePausedSince != null;
    }

    public boolean scanBlocked() {
        return scanBlockedSince != null;
    }

    /** True when no complete sweep was recorded, or the last is older than 10 minutes (plan PD-4, IntakeGate). */
    public boolean sweepStale(Instant now) {
        return lastSweepOkAt == null || now.isAfter(lastSweepOkAt.plus(SWEEP_STALE_AFTER));
    }

    /**
     * A failing path (survey-media spec A-18): {@code failureSeq + 1} and the failure time; with a reason, a pause that keeps the
     * first pause time and reason and bumps {@code stateEpoch} only when intake was open.
     */
    public IntakeHealth failure(PauseReason pauseOrNull, Instant now) {
        if (pauseOrNull == null) {
            return new IntakeHealth(failureSeq + 1, stateEpoch, lastSweepOkAt, lastProbeOkAt, now, lastWorkerBeatAt,
                    lastDeletionBeatAt, scanBlockedSince, intakePausedSince, pauseReason);
        }
        return new IntakeHealth(failureSeq + 1, stateEpoch + (paused() ? 0 : 1), lastSweepOkAt, lastProbeOkAt, now,
                lastWorkerBeatAt, lastDeletionBeatAt, scanBlockedSince, paused() ? intakePausedSince : now,
                paused() ? pauseReason : pauseOrNull);
    }

    /**
     * The resume of a complete sweep (survey-media spec A-18): only when paused and no failure happened since {@code seqAtStart};
     * otherwise this row, unchanged.
     */
    public IntakeHealth resumed(long seqAtStart, Instant now) {
        if (failureSeq != seqAtStart || !paused()) {
            return this;
        }
        return new IntakeHealth(failureSeq, stateEpoch + 1, now, lastProbeOkAt, lastFailureAt, lastWorkerBeatAt,
                lastDeletionBeatAt, scanBlockedSince, null, null);
    }
}
