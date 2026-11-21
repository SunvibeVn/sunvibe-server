package vn.sunvibe.listing.core.domain.model.listing;

/**
 * The private note of a freshness record (listing-publish BR-17; E06-S3 spec E06S3-D10, plan E06S3-P5): the one home of
 * its rule. Its stored form is the revision text's (NFC, CR LF and CR turned into LF, trimmed, an empty result none;
 * {@link RevisionContent#normalized}); a storable note holds at most 500 Unicode code points (ASSUMPTION A-1), no
 * control character but LF and no format character. The request accepts at most {@link #EDGE_UNITS} UTF-16 units before
 * it normalizes. The note never reaches a log, an error body, a history row or an event: no text of this type shows it.
 *
 * @param text the normalized, storable note; never empty
 */
public record FreshnessNote(String text) {

    /** The request bound in UTF-16 units, checked before the note is normalized (E06S3-D10). */
    public static final int EDGE_UNITS = 2000;
    /** listing-publish E06-S3 ASSUMPTION A-1: at most 500 code points of the stored note. */
    public static final int MAX_CODE_POINTS = 500;

    public FreshnessNote {
        if (text == null || !text.equals(normalized(text)) || !storable(text)) {
            throw new IllegalArgumentException("a freshness note is stored only in its checked form");
        }
    }

    /** The stored form of a typed note: E06S2-D4's form of a revision text; an empty result is null. */
    public static String normalized(String rawOrNull) {
        return RevisionContent.normalized(rawOrNull);
    }

    /** At most 500 code points, no control character other than LF, no format character; null is storable. */
    public static boolean storable(String normalizedOrNull) {
        if (normalizedOrNull == null) {
            return true;
        }
        return RevisionContent.codePoints(normalizedOrNull) <= MAX_CODE_POINTS
                && normalizedOrNull.codePoints().allMatch(codePoint -> codePoint == '\n'
                || !isControlOrFormat(codePoint));
    }

    /** The note of a typed text, or null when the text is empty once normalized; a note not storable is refused. */
    public static FreshnessNote ofOrNull(String rawOrNull) {
        String text = normalized(rawOrNull);
        if (text == null) {
            return null;
        }
        if (!storable(text)) {
            throw new IllegalArgumentException("a freshness note breaks its stored bounds");
        }
        return new FreshnessNote(text);
    }

    /** Categories Cc (NUL included, which PostgreSQL cannot store) and Cf (zero-width and direction controls). */
    private static boolean isControlOrFormat(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    /** listing-publish BR-17: the note is private and never reaches a log through this text. */
    @Override
    public String toString() {
        return "FreshnessNote[hidden]";
    }
}
