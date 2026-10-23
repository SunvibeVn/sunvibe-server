package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * The lead already has a scheduled survey appointment (survey-schedule US-1 AC-4, spec I-1). Raised by the booking
 * check and by the repository when the one-scheduled-per-lead index refuses a concurrent booking.
 */
public class AppointmentAlreadyScheduledException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AppointmentAlreadyScheduledException() {
        super("This lead already has a scheduled survey.");
    }

    public AppointmentAlreadyScheduledException(Throwable cause) {
        super("This lead already has a scheduled survey.", cause);
    }
}
