package vn.sunvibe.survey.core.application.exception;

import java.io.Serial;

import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;

/**
 * The file's bytes are refused by a content identity rule of survey-media spec A-16: 409 MEDIA_DUPLICATE_CONTENT (a
 * selected photo of this checklist) or MEDIA_FROM_OTHER_VISIT (a committed photo of another visit of the lead), on
 * {@code contentSha256} with the matching photo's id. The id is null only when the matching photo is gone by the time
 * the unique backstop's refusal is answered.
 */
public class MediaContentIdentityException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final IdentityVerdict.Kind kind;
    private final String matchingMediaIdOrNull;

    public MediaContentIdentityException(IdentityVerdict.Kind kind, String matchingMediaIdOrNull) {
        super(kind == IdentityVerdict.Kind.FROM_OTHER_VISIT ? "This photo belongs to another visit of the lead."
                : "This photo is already in the checklist.");
        if (kind == IdentityVerdict.Kind.FREE) {
            throw new IllegalArgumentException("A free verdict refuses nothing.");
        }
        this.kind = kind;
        this.matchingMediaIdOrNull = matchingMediaIdOrNull;
    }

    public IdentityVerdict.Kind getKind() {
        return kind;
    }

    public String getMatchingMediaIdOrNull() {
        return matchingMediaIdOrNull;
    }
}
