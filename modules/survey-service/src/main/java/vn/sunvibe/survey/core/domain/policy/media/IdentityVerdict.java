package vn.sunvibe.survey.core.domain.policy.media;

/** The content identity answer of a create or a commit (survey-media spec A-16). */
public record IdentityVerdict(Kind kind, String matchingMediaIdOrNull) {

    /** The first rule of A-16 that matched. */
    public enum Kind {
        /** No rule matched: the bytes may be uploaded as a new photo. */
        FREE,
        /** (a) the same hash on a selected photo of this checklist: 409 MEDIA_DUPLICATE_CONTENT. */
        DUPLICATE_CONTENT,
        /** (b) the same hash committed on another visit of the same lead: 409 MEDIA_FROM_OTHER_VISIT. */
        FROM_OTHER_VISIT
    }

    public static final IdentityVerdict FREE = new IdentityVerdict(Kind.FREE, null);

    public IdentityVerdict {
        if (kind == null || (kind == Kind.FREE) != (matchingMediaIdOrNull == null)) {
            throw new IllegalArgumentException("A match names its photo; a free answer names none.");
        }
    }
}
