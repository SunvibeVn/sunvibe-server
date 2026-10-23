package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The lead or the appointment changed since the caller read it (survey-schedule BR-2, spec A-2). Names the request
 * field that carried the stale version: {@code expectedLeadVersion} or {@code expectedAppointmentVersion}.
 */
public class SurveyConcurrentModificationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public SurveyConcurrentModificationException(String field) {
        super("Someone else just updated this lead.");
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
