package vn.sunvibe.survey.core.domain.exception;

import java.io.Serial;

/**
 * A value of a survey command breaks a rule (survey-schedule BR-4, spec 4.8, A-14). Carries the request field as the
 * client sent it (for example {@code meetingLocation.addressText}) and an English message that never quotes the value.
 * A null field means the request as a whole is refused without a field, as for "Nothing changed." (survey-schedule
 * spec A-4).
 */
public class InvalidSurveyValueException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidSurveyValueException(String fieldOrNull, String message) {
        super(message);
        this.field = fieldOrNull;
    }

    /** The request field that failed, or null when the refusal names none. */
    public String getField() {
        return field;
    }
}
