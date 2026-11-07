package vn.sunvibe.survey.core.application.shared.media;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.PauseReason;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;

/**
 * The intake gate of create, the content upload and the commit (survey-media spec A-18, plan PD-4). It reads the health
 * row in the caller's transaction (no second connection) and refuses while intake is paused, and also when no complete
 * sweep was recorded in the last 10 minutes, so intake never opens without a recent sweep. That stale-sweep finding
 * is written as a SWEEP_STALE pause by {@link #recordRefusal} once the caller's transaction has completed: the health
 * row is written only in its own short transaction holding no other lock.
 */
@Component
public class IntakeGate {

    private final IntakeHealthRepository health;
    private final MediaClock clock;

    public IntakeGate(IntakeHealthRepository health, MediaClock clock) {
        this.health = health;
        this.clock = clock;
    }

    /**
     * @throws MediaIntakePausedException while intake is paused or the last complete sweep is stale
     */
    public void requireOpen() {
        IntakeHealth row = health.read();
        if (row.paused()) {
            throw new MediaIntakePausedException(false);
        }
        if (row.sweepStale(clock.now())) {
            throw new MediaIntakePausedException(true);
        }
    }

    /** After the refused request's transaction completed: records a stale sweep as a SWEEP_STALE pause. */
    public void recordRefusal(MediaIntakePausedException refusal) {
        if (refusal.sweepStale()) {
            health.recordFailure(PauseReason.SWEEP_STALE, true, clock.now());
        }
    }
}
