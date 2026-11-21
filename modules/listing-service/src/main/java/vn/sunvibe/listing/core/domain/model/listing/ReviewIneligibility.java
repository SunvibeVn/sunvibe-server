package vn.sunvibe.listing.core.domain.model.listing;

/**
 * Why the caller may not decide a revision now (listing-publish BR-7, US-4 AC-4; listing BR-16; E06-S4 spec E06S4-D2 to
 * D5, section 4.2), in this order, the order every answer lists them in.
 */
public enum ReviewIneligibility {
    /** The caller holds neither LISTING_REVIEWER nor ADMIN. */
    NOT_A_REVIEWER,
    /** The caller created or edited the revision, or a revision it was copied from (E06S4-D3). */
    AUTHOR,
    /** The caller holds the listing agreement's basis lead now, whatever roles they hold (E06S4-D4). */
    ASSIGNED_SALE,
    /** The revision is not Pending review: decided, withdrawn or a Draft. */
    NOT_PENDING,
    /** The listing is archived, stored or because its agreement ended. */
    LISTING_ARCHIVED
}
