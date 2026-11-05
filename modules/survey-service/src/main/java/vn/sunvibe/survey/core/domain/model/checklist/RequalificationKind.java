package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * Why a PASSED revision was withdrawn from use (property-create requalification spec A-5). WRONG_ASSET and WRONG_OWNER
 * are read by the inventory intake (property-create BR-20).
 */
public enum RequalificationKind {
    /** The survey was of the wrong asset. */
    WRONG_ASSET,
    /** The person treated as the owner is not the owner. */
    WRONG_OWNER,
    /** The surveyor was the wrong person. */
    WRONG_PERSON,
    /** The surveyed scope was wrong. */
    WRONG_SCOPE,
    /** The owner's need changed. */
    NEED_CHANGED,
    /** A photo turned out wrong or was lost (property-create requalification D5-3). */
    PHOTO_PROBLEM,
    /** A hazard came to light after the review. */
    HAZARD,
    /** Anything else; the reason says what. */
    OTHER
}
