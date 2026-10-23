package vn.sunvibe.survey.core.domain.model.appointment;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

import java.time.LocalDate;

/**
 * What a proxy record or a resumption relies on (survey-schedule BR-9, "căn cứ"): the Vietnam date of the
 * confirmation and a short text. Staff text: never logged.
 */
public record Basis(LocalDate confirmedOn, String text) {

    public static final int MAX_TEXT_LENGTH = 500;

    public Basis {
        if (confirmedOn == null || text == null) {
            throw new IllegalArgumentException("A basis needs its date and text.");
        }
    }

    /**
     * A basis with a date and a text of 1..500 characters without a phone number (survey-schedule spec A-14).
     * {@code fieldPrefix} is the request path of the basis ({@code basis} or {@code proxy.basis}).
     *
     * @throws InvalidSurveyValueException field {@code <prefix>.confirmedOn} or {@code <prefix>.text}
     */
    public static Basis of(String fieldPrefix, LocalDate confirmedOn, String text) {
        if (confirmedOn == null) {
            throw new InvalidSurveyValueException(fieldPrefix + ".confirmedOn",
                    fieldPrefix + ".confirmedOn is required.");
        }
        return new Basis(confirmedOn, SurveyText.require(fieldPrefix + ".text", text, MAX_TEXT_LENGTH));
    }

    /**
     * survey-schedule spec A-25: the date is not after today and not before the event it confirms (when given).
     *
     * @throws InvalidSurveyValueException field {@code <prefix>.confirmedOn}
     */
    public void requireWithin(String fieldPrefix, LocalDate notBeforeOrNull, LocalDate today) {
        if (confirmedOn.isAfter(today)) {
            throw new InvalidSurveyValueException(fieldPrefix + ".confirmedOn",
                    fieldPrefix + ".confirmedOn cannot be in the future.");
        }
        if (notBeforeOrNull != null && confirmedOn.isBefore(notBeforeOrNull)) {
            throw new InvalidSurveyValueException(fieldPrefix + ".confirmedOn",
                    fieldPrefix + ".confirmedOn cannot be before the event it confirms.");
        }
    }

    @Override
    public String toString() {
        return "Basis[" + confirmedOn + "]";
    }
}
