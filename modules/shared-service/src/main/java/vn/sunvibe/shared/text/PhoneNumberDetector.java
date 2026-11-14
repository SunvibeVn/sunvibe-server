package vn.sunvibe.shared.text;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Finds a Vietnamese phone number inside free text written by staff, so a third party's number is not stored in a
 * note, a reason or an access hint (survey BR-4 "không thu số điện thoại bên thứ ba"; survey-schedule spec A-14, its
 * shape of a number as amended for SV-212 by the phone detector spec of 2026-11-15, section 4).
 * A number is an optional {@code +}, then {@code 84} or {@code 0}, then 9 or 10 digits; spaces, dots, dashes and
 * brackets may sit between the digits, so "0905 123 456", "+84.905.123.456" and "(0236) 3812 345" are all found,
 * also with no-break spaces, en dashes, invisible characters or full-width digits.
 * A number is matched on the text itself: it never starts or ends inside a run of digits glued by a dot or a dash, and
 * a space and a colon are always edges, so a longer run of digits (a code, an account number) is not a phone number
 * and a floor number before a phone does not hide it. A valid calendar date (d.m.yyyy, d-m-yyyy) or clock time
 * (h:mm) is never part of a number. The UI copy ({@code phone-in-text.ts}) follows the same rule and vector table.
 * Door codes and other secrets cannot be recognised; the forms remind staff instead.
 */
public final class PhoneNumberDetector {

    /** Invisible format characters (zero-width space, soft hyphen, word joiner, direction marks). */
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    /** A valid date: day 1-31, month 1-12, year 1900-2099, one repeated dot or dash connector, no digit beside it. */
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(?:0?[1-9]|[12]\\d|3[01])([.\\p{Pd}\\u2212])(?:0?[1-9]|1[0-2])\\1(?:19|20)\\d{2}(?!\\d)");
    /** A clock time: hour 0-23, a colon and minutes 00-59, with no digit or colon on either side. */
    private static final Pattern TIME = Pattern.compile("(?<![\\d:])(?:[01]?\\d|2[0-3]):[0-5]\\d(?![\\d:])");
    /**
     * A number: optional {@code +}, {@code 84} or {@code 0}, then 9 or 10 digits with any run of separators (spaces of
     * any kind, any dash, the minus sign, dots, brackets) between them; not directly after a digit or after a dash or
     * dot that follows a digit, and not directly before a digit or before a dash or dot that precedes a digit.
     */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?<!\\d[\\p{Pd}\\u2212.])(?:\\+[\\s\\p{Z}\\p{Pd}\\u2212.()]*)?"
                    + "(?:8[\\s\\p{Z}\\p{Pd}\\u2212.()]*4|0)(?:[\\s\\p{Z}\\p{Pd}\\u2212.()]*\\d){9,10}"
                    + "(?!\\d)(?![\\p{Pd}\\u2212.]\\d)");

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
        String withoutDates = DATE.matcher(compatible).replaceAll("_");
        String masked = TIME.matcher(withoutDates).replaceAll("_");
        return PHONE.matcher(masked).find();
    }
}
