package vn.sunvibe.listing.core.domain.model.listing;

/**
 * What a row of a listing's history records (listing BR-3: every transition records its actor, time and revision).
 * Later slices add their actions; the stored column has no CHECK, so they need no migration.
 */
public enum ListingHistoryAction {
    CREATED
}
