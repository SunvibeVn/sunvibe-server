package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * A command asks a survey appointment that is no longer scheduled (VISITED or CANCELLED, both final) to change
 * (survey-schedule spec 4.4). The message holds the status only.
 */
public class AppointmentStateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AppointmentStateException(String status) {
        super("The survey appointment is " + status + " and cannot be changed.");
    }
}
