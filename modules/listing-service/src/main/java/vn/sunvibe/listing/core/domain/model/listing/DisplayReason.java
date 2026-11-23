package vn.sunvibe.listing.core.domain.model.listing;

/**
 * A display condition of listing BR-4 and listing-publish BR-11 that does not hold (listing-publish E06-S6 spec
 * E06S6-D3), in the fixed order a refusal and a read answer them, each at most once. E05's reason groups ride with
 * {@link #ADVERTISING_RIGHT}.
 */
public enum DisplayReason {
    /** For a put live, the listing is not Unpublished or Published; for the live revision, not Published. */
    LISTING_STATE,
    /** E05 answers nothing for the listing's own agreement, or it ended, or it does not allow advertising. */
    ADVERTISING_RIGHT,
    /** E05 has no terms in effect, or the revision's snapshot does not equal them by value. */
    TERMS_NOT_IN_EFFECT,
    /** property knows no such unit, it is not available, or an intake entry of it lists another availability. */
    UNIT_NOT_AVAILABLE,
    /** The latest freshness record is not a valid confirmation, or its deadline has passed, or none was recorded. */
    FRESHNESS
}
