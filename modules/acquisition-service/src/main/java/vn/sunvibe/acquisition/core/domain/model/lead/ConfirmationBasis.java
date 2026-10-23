package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.shared.text.PhoneNumberDetector;

import java.text.Normalizer;
import java.time.LocalDate;

/**
 * What a staff command relies on (survey-schedule BR-9, "căn cứ"): the Vietnam date on which the owner confirmed it
 * and a short text saying how. The text is staff text: never logged.
 *
 * @param confirmedOn the date of the confirmation, as the client sent it (may be null until checked)
 * @param text        the text as the client sent it (may be null until checked)
 */
public record ConfirmationBasis(LocalDate confirmedOn, String text) {

    public static final int MAX_TEXT_LENGTH = 500;

    /**
     * The basis with its text cleaned (Unicode NFC, trimmed), after checking it: a date not after {@code today} and
     * not before {@code notBeforeOrNull} (survey-schedule spec A-25), and a text of 1..500 characters holding no phone
     * number (spec A-14).
     *
     * @throws InvalidLeadCommandException field {@code basis.confirmedOn} or {@code basis.text}
     */
    public ConfirmationBasis requireValid(LocalDate notBeforeOrNull, LocalDate today) {
        if (confirmedOn == null) {
            throw new InvalidLeadCommandException("basis.confirmedOn", "basis.confirmedOn is required.");
        }
        if (confirmedOn.isAfter(today)) {
            throw new InvalidLeadCommandException("basis.confirmedOn", "basis.confirmedOn cannot be in the future.");
        }
        if (notBeforeOrNull != null && confirmedOn.isBefore(notBeforeOrNull)) {
            throw new InvalidLeadCommandException("basis.confirmedOn",
                    "basis.confirmedOn cannot be before the event it confirms.");
        }
        String clean = text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        if (clean.isEmpty() || clean.length() > MAX_TEXT_LENGTH) {
            throw new InvalidLeadCommandException("basis.text",
                    "basis.text must be between 1 and " + MAX_TEXT_LENGTH + " characters.");
        }
        if (PhoneNumberDetector.containsVietnamesePhoneNumber(clean)) {
            throw new InvalidLeadCommandException("basis.text", "basis.text must not contain a phone number.");
        }
        return new ConfirmationBasis(confirmedOn, clean);
    }

    /** The text is staff text: never print it. */
    @Override
    public String toString() {
        return "ConfirmationBasis[" + confirmedOn + "]";
    }
}
