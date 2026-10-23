package vn.sunvibe.survey.core.domain.policy;

import vn.sunvibe.shared.text.PhoneNumberDetector;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;

import java.text.Normalizer;

/**
 * The one cleaning and checking rule of the free text staff type into survey commands (survey-schedule BR-4, spec
 * A-14, A-24): Unicode NFC and trimmed, inner spaces and line breaks kept; optional text that is blank counts as
 * absent, required text keeps at least one character; a Vietnamese phone number is refused (survey BR-4: no number of
 * a third party). Messages name the field and never quote the text.
 */
public final class SurveyText {

    private SurveyText() {
    }

    /**
     * Required text of 1..max characters without a phone number, cleaned.
     *
     * @throws InvalidSurveyValueException on the field
     */
    public static String require(String field, String value, int max) {
        String text = clean(value);
        if (text == null || text.isEmpty() || text.length() > max) {
            throw new InvalidSurveyValueException(field, field + " must be between 1 and " + max + " characters.");
        }
        if (PhoneNumberDetector.containsVietnamesePhoneNumber(text)) {
            throw new InvalidSurveyValueException(field, field + " must not contain a phone number.");
        }
        return text;
    }

    /**
     * Optional text: null when absent or blank, else as {@link #require}.
     *
     * @throws InvalidSurveyValueException on the field
     */
    public static String optional(String field, String value, int max) {
        String text = clean(value);
        return text == null || text.isEmpty() ? null : require(field, text, max);
    }

    /**
     * Text the system copies from another command that already checked it (a hold note, a close reason): cleaned,
     * blank is absent, no phone rule. Longer than max is a bug.
     */
    public static String system(String value, int max) {
        String text = clean(value);
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new IllegalArgumentException("System text is longer than " + max + " characters.");
        }
        return text;
    }

    private static String clean(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC).strip();
    }
}
