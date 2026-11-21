package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The note of a review decision (listing-publish US-4 AC-3, BR-7; E06-S4 spec E06S4-D6, D20): the one home of its rule.
 * Its stored form is the revision text's (NFC, CR LF and CR turned into LF, trimmed, an empty result none;
 * {@link RevisionContent#normalized}); a storable note holds 1 to {@value #MAX_CODE_POINTS} Unicode code points (E06-S4
 * ASSUMPTION A-2), no control character but LF and no format character. The request accepts at most
 * {@value #EDGE_UNITS} UTF-16 units before it normalizes. The note never reaches a log, an error body, a history row or
 * an event: no text of this type shows it.
 *
 * @param text the normalized, storable note; never empty
 */
public record ReviewNote(String text) {

    /** The request bound in UTF-16 units, checked before the note is normalized (E06S4-D6). */
    public static final int EDGE_UNITS = 4000;
    /** listing-publish E06-S4 ASSUMPTION A-2: at most 1,000 code points of the stored note. */
    public static final int MAX_CODE_POINTS = 1000;

    public ReviewNote {
        if (text == null || !text.equals(normalized(text)) || !storable(text)) {
            throw new IllegalArgumentException("a review note is stored only in its checked form");
        }
    }

    /** The stored form of a typed note: E06S2-D4's form of a revision text; an empty result is null. */
    public static String normalized(String rawOrNull) {
        return RevisionContent.normalized(rawOrNull);
    }

    /** At most 1,000 code points, no control character other than LF, no format character; null is storable. */
    public static boolean storable(String normalizedOrNull) {
        if (normalizedOrNull == null) {
            return true;
        }
        return RevisionContent.codePoints(normalizedOrNull) <= MAX_CODE_POINTS
                && normalizedOrNull.codePoints().allMatch(codePoint -> codePoint == '\n'
                || !isControlOrFormat(codePoint));
    }

    /** The note of a typed text, or null when the text is empty once normalized; a note not storable is refused. */
    public static ReviewNote ofOrNull(String rawOrNull) {
        String text = normalized(rawOrNull);
        if (text == null) {
            return null;
        }
        if (!storable(text)) {
            throw new IllegalArgumentException("a review note breaks its stored bounds");
        }
        return new ReviewNote(text);
    }

    /** Categories Cc (NUL included, which PostgreSQL cannot store) and Cf (zero-width and direction controls). */
    private static boolean isControlOrFormat(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    /** listing BR-2, E06S4-D20: the note is private and never reaches a log through this text. */
    @Override
    public String toString() {
        return "ReviewNote[hidden]";
    }
}
