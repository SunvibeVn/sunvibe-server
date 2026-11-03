package vn.sunvibe.survey.core.domain.model.media;

/** The three processed JPEG variants and their bounds (survey-media spec A-4, A-28). */
public enum VariantKind {
    /** The normalized photo: long side at most 4,096 px, at most 12 MiB. */
    FULL(12L * 1024 * 1024, 4096, 4096),
    /** Long side at most 1,600 px, at most 2 MiB. */
    PREVIEW(2L * 1024 * 1024, 1600, 1600),
    /** Within 400 x 400 px, at most 256 KiB. */
    THUMBNAIL(256L * 1024, 400, 400);

    private final long maxBytes;
    private final int maxLongSide;
    private final int maxShortSide;

    VariantKind(long maxBytes, int maxLongSide, int maxShortSide) {
        this.maxBytes = maxBytes;
        this.maxLongSide = maxLongSide;
        this.maxShortSide = maxShortSide;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** True when the dimensions are positive and within the variant's bounds (survey-media spec A-4, A-28). */
    public boolean fits(int width, int height) {
        return width > 0 && height > 0 && Math.max(width, height) <= maxLongSide
                && Math.min(width, height) <= maxShortSide;
    }
}
