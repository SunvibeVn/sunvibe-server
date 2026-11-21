package vn.sunvibe.listing.core.domain.model.listing;

/**
 * What a row of a listing's history records (listing BR-3: every transition records its actor, time and revision).
 * Later slices add their actions; the stored column has no CHECK, so they need no migration.
 */
public enum ListingHistoryAction {
    CREATED,
    /** listing-publish US-2 AC-6: a new empty Draft. */
    REVISION_STARTED,
    /** listing-publish US-2 AC-1, AC-3: a Draft's title, description and features saved. */
    REVISION_SAVED,
    /** listing-publish US-2 AC-2: a terms snapshot taken from E05. */
    TERMS_CAPTURED,
    /** listing-publish US-2 AC-4, AC-5: a Draft submitted for review. */
    REVISION_SUBMITTED,
    /** listing-publish US-2 AC-5: a Pending review revision withdrawn. */
    REVISION_WITHDRAWN,
    /** listing-publish US-6 AC-1: a contact with the person letting the unit recorded; no revision, outcome or note. */
    FRESHNESS_RECORDED,
    /** listing-publish US-4 AC-3: a Pending review revision approved; nothing goes live (E06-S4 spec E06S4-D9). */
    REVISION_APPROVED,
    /** listing-publish US-4 AC-3: a Pending review revision returned as Needs changes; no reason or note here. */
    REVISION_RETURNED,
    /** listing-publish US-4 AC-3: a Pending review revision rejected; no group or note here. */
    REVISION_REJECTED,
    /** listing-publish BR-6: an approved revision still waiting, cancelled by a later approval (E06S4-D9). */
    APPROVAL_CANCELLED,
    /** listing-publish US-2 AC-6: a Needs changes revision copied into a new Draft (E06-S4 spec E06S4-D14). */
    REVISION_COPIED
}
