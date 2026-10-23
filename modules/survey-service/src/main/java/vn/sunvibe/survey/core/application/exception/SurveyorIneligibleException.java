package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

/**
 * The surveyor (the lead's assignee) can no longer take leads, so the appointment cannot be booked, changed or
 * confirmed, or the caller cannot record their own visit (survey-schedule BR-6, spec 4.7). The field is null except
 * {@code performedByUserId} for an own visit record.
 */
public class SurveyorIneligibleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public SurveyorIneligibleException(String fieldOrNull) {
        super("The assignee can no longer take leads. Reassign the lead first.");
        this.field = fieldOrNull;
    }

    public String getField() {
        return field;
    }
}
