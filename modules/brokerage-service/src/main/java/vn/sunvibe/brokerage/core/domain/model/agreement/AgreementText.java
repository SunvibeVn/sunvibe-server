package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The one character rule of the agreement's free texts (E05-S4 spec S4-D3; E05-S5 spec S5-D7, plan E05S5-P3): the reason
 * of a fee free of charge and the owner's rejection reason hold no character that can hide or reorder the text a reader
 * sees. One rule for both, so the two texts cannot drift apart.
 */
final class AgreementText {

    private AgreementText() {
    }

    /**
     * Whether the trimmed text holds a control character (U+0000 to U+001F, U+007F to U+009F; PostgreSQL cannot store
     * U+0000) or a bidirectional formatting character (U+200E, U+200F, U+202A to U+202E, U+2066 to U+2069).
     */
    static boolean hidesText(String text) {
        return text.strip().codePoints().anyMatch(AgreementText::hides);
    }

    private static boolean hides(int codePoint) {
        return codePoint <= 0x1F || (codePoint >= 0x7F && codePoint <= 0x9F) || codePoint == 0x200E
                || codePoint == 0x200F || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
