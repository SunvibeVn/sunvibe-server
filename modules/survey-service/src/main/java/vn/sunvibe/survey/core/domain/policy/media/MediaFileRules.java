package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;

import java.util.Optional;

/** File bounds of a survey photo (survey-media spec 4.5, A-2, A-3). */
public final class MediaFileRules {

    /** survey-media spec A-2: "20 MB" read as 20 MiB per file. */
    public static final long MAX_FILE_BYTES = 20_971_520L;
    /** survey-media spec A-3: decoded at most 120,000,000 pixels. */
    public static final long MAX_PIXELS = 120_000_000L;
    /** survey-media spec A-3: at most 16,384 px per side. */
    public static final long MAX_SIDE = 16_384L;

    private MediaFileRules() {
    }

    /** survey-media spec A-2: a declared or received size of 1 byte to 20 MiB. */
    public static boolean sizeAccepted(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_FILE_BYTES;
    }

    /** survey-media spec 4.5, A-3: TOO_MANY_PIXELS when a side or the composed area is over its bound, checked before decoding. */
    public static Optional<MediaStatusReason> pixelCheck(long width, long height) {
        if (width > MAX_SIDE || height > MAX_SIDE || width * height > MAX_PIXELS) {
            return Optional.of(MediaStatusReason.TOO_MANY_PIXELS);
        }
        return Optional.empty();
    }
}
