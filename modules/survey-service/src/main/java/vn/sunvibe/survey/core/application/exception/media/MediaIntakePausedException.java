package vn.sunvibe.survey.core.application.exception.media;

import java.io.Serial;

/**
 * Intake is paused (survey-media spec A-18): 503 MEDIA_INTAKE_PAUSED for create, the content upload and the commit of a
 * stream in flight. {@code sweepStale} tells that the gate found the last complete sweep too old, which the caller
 * records as a SWEEP_STALE pause once its own transaction has completed (plan PD-4).
 */
public class MediaIntakePausedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final boolean sweepStale;

    public MediaIntakePausedException(boolean sweepStale) {
        super("Photo uploads are paused for a moment.");
        this.sweepStale = sweepStale;
    }

    public boolean sweepStale() {
        return sweepStale;
    }
}
