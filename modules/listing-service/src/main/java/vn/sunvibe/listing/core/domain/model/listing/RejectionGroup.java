package vn.sunvibe.listing.core.domain.model.listing;

/** Why a revision is rejected (listing-publish BR-7; E06-S4 spec E06S4-D6), a closed list in this order. */
public enum RejectionGroup {
    /** "nghi tin giả hay gian lận" */
    SUSPECTED_FRAUD,
    /** "căn không đúng" */
    WRONG_UNIT,
    /** "vi phạm nghiêm trọng" */
    SERIOUS_VIOLATION,
    /** "khác (ghi rõ)": the note says what */
    OTHER
}
