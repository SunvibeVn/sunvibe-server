package vn.sunvibe.survey.core.domain.repository.media;

import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;

import java.time.Instant;

/**
 * The one intake health row of every instance (survey-media spec 5.2, A-18). The read joins the caller's transaction
 * (the list reads it inside its repeatable-read snapshot); every write runs in its own short READ COMMITTED transaction
 * that holds no other lock, outside the lock order of spec 5.4, and is called only after or outside a lock-holding
 * transaction. Stored times are application-clock values.
 */
public interface IntakeHealthRepository {

    /** The row, read without a lock in the caller's transaction (or on its own connection outside one). */
    IntakeHealth read();

    /**
     * A failing path: {@code failure_seq + 1} and the failure time; when {@code pause}, a pause that keeps the first
     * pause time and reason and bumps {@code state_epoch} only when intake was open.
     */
    void recordFailure(PauseReason reason, boolean pause, Instant appNow);

    /** A wrong scanner answer blocks every scan (kept when already blocked). */
    void setScanBlocked(Instant appNow);

    /**
     * A passing probe (survey-media spec 5.2, A-18): its time in {@code last_probe_ok_at}; a passing scanner probe also
     * clears the scan block. No pause changes.
     */
    void recordProbePassed(boolean clearScanBlock, Instant appNow);

    /**
     * The resume of a complete sweep: only while paused and with the {@code failure_seq} read before the sweep's
     * probes; clears the pause, bumps {@code state_epoch} and records the sweep time. False when nothing changed.
     */
    boolean resumeIf(long seqAtStart, Instant appNow);

    /** A complete sweep's time, whatever the pause (the stale-sweep rule of the gate reads it). */
    void recordSweepCompleted(Instant appNow);

    /** The processing worker's heartbeat. */
    void beatWorker(Instant appNow);

    /** The content deletion executor's heartbeat. */
    void beatDeletion(Instant appNow);
}
