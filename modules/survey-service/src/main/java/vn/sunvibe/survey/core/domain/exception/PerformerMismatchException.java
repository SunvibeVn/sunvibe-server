package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * The person named as having reached the property did not hold the appointment at the arrival time
 * (survey-schedule US-3 AC-1a, spec A-8).
 */
public class PerformerMismatchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PerformerMismatchException() {
        super("This person did not hold the appointment at that time.");
    }
}
