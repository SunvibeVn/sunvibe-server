package vn.sunvibe.shared.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Finds a Vietnamese phone number inside free text written by staff, so a third party's number is not stored in a
 * note, a reason or an access hint (survey BR-4 "không thu số điện thoại bên thứ ba"; survey-schedule spec A-14).
 * A number is an optional {@code +84}, {@code 84} or {@code 0}, then 9 or 10 digits; spaces, dots, dashes and
 * brackets between the digits are ignored, so "0905 123 456", "+84.905.123.456" and "(0236) 3812 345" are all found,
 * also with no-break spaces, en dashes, invisible characters or full-width digits.
 * A longer run of digits (a code, an account number) is not a phone number. Door codes and other secrets cannot be
 * recognised; the forms remind staff instead.
 */
public final class PhoneNumberDetector {

    /**
     * Separators between two digits (or between "+" or "(" and a digit): spaces of any kind, dots, any dash (the
     * en dash an editor puts between numbers too), the minus sign and brackets. Removed before matching.
     */
    private static final Pattern SEPARATORS_BETWEEN_DIGITS =
            Pattern.compile("(?<=[\\d+])[\\s\\p{Z}\\p{Pd}\\u2212.()]+(?=[\\d(+])");
    /** Invisible format characters (zero-width space, soft hyphen, word joiner, direction marks). */
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?84|0)\\d{9,10}(?!\\d)");

    private PhoneNumberDetector() {
    }

    /** True when the text holds a Vietnamese phone number; false for null or blank text. */
    public static boolean containsVietnamesePhoneNumber(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        // NFKC turns no-break and thin spaces into plain spaces and full-width digits into ASCII ones, as pasted text
        // often has them; only the detection uses this form, the caller keeps its own text
        String compatible = INVISIBLE.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll("");
        String joined = SEPARATORS_BETWEEN_DIGITS.matcher(compatible).replaceAll("");
        return PHONE.matcher(joined).find();
    }
}
