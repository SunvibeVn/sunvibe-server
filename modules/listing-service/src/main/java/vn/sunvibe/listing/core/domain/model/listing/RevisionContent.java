package vn.sunvibe.listing.core.domain.model.listing;

import java.text.Normalizer;

/**
 * What the Sale types in a revision: title, description and features (listing-publish US-2 AC-1, AC-3, BR-2, BR-3;
 * E06-S2 spec E06S2-D4, E06S2-D5). The one home of the stored form of the text and of its storable bounds: the request
 * constraints call {@link #normalized}, {@link #titleStorable} and {@link #descriptionStorable}, and the constructor
 * checks them again. Characters are Unicode code points of the NFC text (a UTF-16 length counts an emoji twice).
 * Minimum lengths, presence and the machine check are submission rules, not storage rules ("Nháp lưu dở được").
 */
public record RevisionContent(String titleOrNull, String descriptionOrNull, RevisionFeatures features) {

    /** listing-publish BR-2: a title of at most 90 characters. */
    public static final int TITLE_MAX = 90;
    /** listing-publish BR-2: a description of at most 3,000 characters. */
    public static final int DESCRIPTION_MAX = 3000;

    /** A new Draft: no text, no feature chosen. */
    public static final RevisionContent EMPTY = new RevisionContent(null, null, RevisionFeatures.NONE_CHOSEN);

    public RevisionContent {
        if (!titleStorable(titleOrNull) || !descriptionStorable(descriptionOrNull) || features == null) {
            throw new IllegalArgumentException("a revision's content is stored only in its checked form");
        }
    }

    /**
     * The stored form of a typed text: NFC, CR LF and CR turned into LF, leading and trailing white space (no-break
     * spaces included) removed; an empty result is null.
     */
    public static String normalized(String textOrNull) {
        if (textOrNull == null) {
            return null;
        }
        String text = Normalizer.normalize(textOrNull, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return start == end ? null : text.substring(start, end);
    }

    /** listing-publish BR-2: at most 90 characters on one line, no control or format character; null is storable. */
    public static boolean titleStorable(String normalized) {
        if (normalized == null) {
            return true;
        }
        return codePoints(normalized) <= TITLE_MAX && normalized.codePoints().allMatch(codePoint ->
                codePoint != '\n' && codePoint != 0x2028 && codePoint != 0x2029 && !isControlOrFormat(codePoint));
    }

    /**
     * listing-publish BR-2: at most 3,000 characters; line feeds allowed, no other control and no format character;
     * null is storable.
     */
    public static boolean descriptionStorable(String normalized) {
        if (normalized == null) {
            return true;
        }
        return codePoints(normalized) <= DESCRIPTION_MAX && normalized.codePoints().allMatch(codePoint ->
                codePoint == '\n' || !isControlOrFormat(codePoint));
    }

    /** The number of Unicode code points of a text. */
    public static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    /** Categories Cc (NUL included, which PostgreSQL cannot store) and Cf (zero-width and direction controls). */
    private static boolean isControlOrFormat(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /** listing-publish US-10 AC-4: unpublished content never reaches a log through this text. */
    @Override
    public String toString() {
        return "RevisionContent[hidden]";
    }
}
