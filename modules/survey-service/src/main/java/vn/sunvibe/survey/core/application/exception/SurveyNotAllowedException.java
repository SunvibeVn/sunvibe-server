package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;
import java.util.List;

/**
 * A survey cannot be booked for this lead now (survey-schedule US-1 AC-1, US-3 AC-3, US-5 AC-3): its status
 * (field {@code status}, with the statuses that allow the booking kind) or its intent (field {@code intent}, with the
 * rental intents) does not allow it.
 */
public class SurveyNotAllowedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;
    private final List<String> allowedValues;

    public SurveyNotAllowedException(String field, List<String> allowedValues) {
        super("A survey cannot be booked for this lead now.");
        this.field = field;
        this.allowedValues = List.copyOf(allowedValues);
    }

    public String getField() {
        return field;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }
}
