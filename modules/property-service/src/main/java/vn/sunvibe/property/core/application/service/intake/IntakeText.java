package vn.sunvibe.property.core.application.service.intake;

import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.shared.text.PhoneNumberDetector;

import java.text.Normalizer;
import java.util.List;

/**
 * The free text staff type into intake commands (property-create intake A-6): Unicode NFC and trimmed, without a phone number
 * or U+0000, which PostgreSQL cannot store. A refusal names the field and never quotes the text.
 */
public final class IntakeText {

    public static final int MAX_REASON = 2000;
    public static final int MAX_NOTE = 500;
    public static final int MAX_QUERY = 100;
    private static final int MAX_CODE = 64;

    private IntakeText() {
    }

    /** Required text of 1..max characters, cleaned. */
    public static String require(String field, String value, int max) {
        if (value == null || value.indexOf('\u0000') >= 0) {
            throw refused(field);
        }
        String text = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        if (text.isEmpty() || text.length() > max || PhoneNumberDetector.containsVietnamesePhoneNumber(text)) {
            throw refused(field);
        }
        return text;
    }

    /** A code a client names, such as a property or ward code: trimmed, bounded, no U+0000; digits are no phone number here. */
    public static String code(String field, String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_CODE || text.indexOf('\u0000') >= 0) {
            throw refused(field);
        }
        return text;
    }

    private static IntakeRefusedException refused(String field) {
        return new IntakeRefusedException(IntakeError.VALIDATION_FAILED, List.of(field));
    }
}
