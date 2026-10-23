package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;
import java.util.List;

/**
 * The lead's status does not allow the survey command (survey-schedule spec 4.3, 7.3): a refused lead move
 * ({@code []}) or a resumption of a lead that is not on hold ({@code ["ON_HOLD"]}).
 */
public class InvalidSurveyStateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<String> allowedValues;

    public InvalidSurveyStateException(List<String> allowedValues) {
        super("This action is not allowed in the lead's current status.");
        this.allowedValues = List.copyOf(allowedValues);
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }
}
