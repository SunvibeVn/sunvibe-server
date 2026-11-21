package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The machine check of a listing's public text (listing-publish BR-2, E-4; listing BR-10, BR-11; E06-S2 spec E06S2-D8,
 * plan E06S2-P9): the rules a text breaks, never the text that broke them. It reads a copy of the text in NFKC (full
 * width digits and letters become ASCII), without the invisible characters, with every run of white space as one space,
 * lower-cased for the word rules.
 *
 * <p>The phone rule (listing-publish BR-2: "số điện thoại dưới mọi cách viết bằng số") joins digit groups across any
 * gap of separators: a run of digits reads as a phone number when it holds a 0 with at least nine digits after it, or 84
 * with at least nine digits after it. A letter, a slash or a comma ends a run; every other character between digits
 * (spaces, dots, dashes of every form, brackets, other punctuation, symbols, emoji, combining marks) joins it. No date
 * or time is masked first, so a dotted date range, a price range or a twelve-digit amount is refused too (accepted
 * over-matches). Stated limits that the reviewer catches (listing-publish BR-7 (3)): a number grouped with slashes or
 * commas, a number with a letter among or in place of its digits, a number in words. Listing does not reuse the shared
 * phone detector, which masks dates for staff notes (E06S2-D9).
 */
public final class ListingTextRules {

    private static final Pattern PHONE_DIGITS = Pattern.compile("0\\d{9}|84\\d{9}");

    private static final Pattern EMAIL =
            Pattern.compile("[\\p{L}\\p{Nd}._%+\\-]+@[\\p{L}\\p{Nd}\\-]+(?:\\.[\\p{L}\\p{Nd}\\-]+)+");

    /** A scheme or www., or a host ending in a listed top-level domain; an e-mail's domain is not a second link. */
    private static final Pattern LINK = Pattern.compile("(?:https?://|www\\.)\\S"
            + "|(?<![\\p{L}\\p{Nd}@._\\-])[\\p{L}\\p{Nd}\\-]+(?:\\.[\\p{L}\\p{Nd}\\-]+)*"
            + "\\.(?:vn|com|net|org|info|biz|me|io|co|asia|xyz|online|site|top|shop|app|link)(?![\\p{L}\\p{Nd}])");

    private static final List<String> PLATFORM_WORDS = List.of("zalo", "facebook", "fb", "messenger", "telegram",
            "viber", "skype", "tiktok", "instagram", "wechat", "whatsapp", "youtube");

    /** An @handle not preceded by a letter or digit (an e-mail is EMAIL), or a platform word as a whole word. */
    private static final Pattern SOCIAL_HANDLE = Pattern.compile("(?<![\\p{L}\\p{Nd}])@[\\p{L}\\p{Nd}._]{2,}"
            + "|" + wholeWords(PLATFORM_WORDS));

    /**
     * listing BR-11's claims, matched as whole phrases on the lower-cased copy, and the same phrases written without any
     * diacritic, matched only where the text carries none ("ăn toàn" is not "an toan").
     */
    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "đã kiểm chứng", "đã xác minh", "xác thực", "verified", "chính chủ", "uy tín", "an toàn", "đảm bảo",
            "cam kết",
            "da kiem chung", "da xac minh", "xac thuc", "chinh chu", "uy tin", "an toan", "dam bao", "cam ket");

    private static final Pattern FORBIDDEN_WORD = Pattern.compile(wholeWords(FORBIDDEN_PHRASES)
            + "|(?<![\\p{Nd}.,])100 ?%");

    private ListingTextRules() {
    }

    /** The machine rules the text breaks, in {@link RevisionRule} order; none for no text. */
    public static Set<RevisionRule> broken(String textOrNull) {
        Set<RevisionRule> broken = EnumSet.noneOf(RevisionRule.class);
        if (textOrNull == null) {
            return broken;
        }
        String copy = copyOf(textOrNull);
        if (holdsPhoneNumber(copy)) {
            broken.add(RevisionRule.PHONE_NUMBER);
        }
        String lower = copy.toLowerCase(Locale.ROOT);
        if (EMAIL.matcher(lower).find()) {
            broken.add(RevisionRule.EMAIL);
        }
        if (LINK.matcher(lower).find()) {
            broken.add(RevisionRule.LINK);
        }
        if (SOCIAL_HANDLE.matcher(lower).find()) {
            broken.add(RevisionRule.SOCIAL_HANDLE);
        }
        if (FORBIDDEN_WORD.matcher(lower).find()) {
            broken.add(RevisionRule.FORBIDDEN_WORD);
        }
        return broken;
    }

    /** NFKC, without U+200B to U+200D, U+2060 and U+FEFF, every run of white space as one space. */
    private static String copyOf(String text) {
        String compatible = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder copy = new StringBuilder(compatible.length());
        boolean inSpace = false;
        for (int index = 0; index < compatible.length(); ) {
            int codePoint = compatible.codePointAt(index);
            index += Character.charCount(codePoint);
            if (isInvisible(codePoint)) {
                continue;
            }
            if (Character.isWhitespace(codePoint) || Character.getType(codePoint) == Character.SPACE_SEPARATOR) {
                if (!inSpace) {
                    copy.append(' ');
                }
                inSpace = true;
                continue;
            }
            inSpace = false;
            copy.appendCodePoint(codePoint);
        }
        return copy.toString();
    }

    /**
     * Each code point is a digit (category Nd, any script), a letter (category L), a run breaker ('/' or ',') or a
     * separator (everything else). A digit run is ended by a letter or a run breaker only.
     */
    private static boolean holdsPhoneNumber(String copy) {
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < copy.length(); ) {
            int codePoint = copy.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.getType(codePoint) == Character.DECIMAL_DIGIT_NUMBER) {
                digits.append(Character.digit(codePoint, 10));
            } else if (Character.isLetter(codePoint) || codePoint == '/' || codePoint == ',') {
                if (readsAsPhoneNumber(digits)) {
                    return true;
                }
                digits.setLength(0);
            }
        }
        return readsAsPhoneNumber(digits);
    }

    private static boolean readsAsPhoneNumber(CharSequence digits) {
        return digits.length() >= 10 && PHONE_DIGITS.matcher(digits).find();
    }

    private static boolean isInvisible(int codePoint) {
        return (codePoint >= 0x200B && codePoint <= 0x200D) || codePoint == 0x2060 || codePoint == 0xFEFF;
    }

    /** The words or phrases, each literally, as a whole between characters that are neither letters nor digits. */
    private static String wholeWords(List<String> phrases) {
        return "(?<![\\p{L}\\p{Nd}])(?:"
                + phrases.stream()
                .map(phrase -> Pattern.quote(Normalizer.normalize(phrase, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining("|"))
                + ")(?![\\p{L}\\p{Nd}])";
    }
}
