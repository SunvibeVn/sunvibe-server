package vn.sunvibe.property.core.domain.policy.intake;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The normalized address the duplicate signal compares (property-create intake A-8): lowercase, NFKD, marks removed,
 * {@code đ} to {@code d}, punctuation to a space, spaces collapsed, cut to 255 characters. It is a comparison key, never
 * shown: it holds an address, so it is not logged.
 */
public final class AddressNormalizer {

    /** The width of {@code address_norm}. */
    public static final int MAX_LENGTH = 255;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private AddressNormalizer() {
    }

    /** @return null for null */
    public static String normalize(String address) {
        if (address == null) {
            return null;
        }
        String lower = address.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String withoutMarks = MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFKD)).replaceAll("");
        String words = NOT_A_WORD.matcher(withoutMarks).replaceAll(" ").strip();
        return words.length() <= MAX_LENGTH ? words : words.substring(0, MAX_LENGTH).stripTrailing();
    }
}
