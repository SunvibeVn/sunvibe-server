package vn.sunvibe.survey.core.domain.model.media;

/** The format detected from a source's bytes (survey-media spec 4.5). */
public enum SourceFormat {
    /** JPEG. */
    JPEG,
    /** PNG (still). */
    PNG,
    /** WebP (still). */
    WEBP,
    /** HEIC/HEIF whose primary item is hvc1 or a grid of hvc1 tiles. */
    HEIF
}
