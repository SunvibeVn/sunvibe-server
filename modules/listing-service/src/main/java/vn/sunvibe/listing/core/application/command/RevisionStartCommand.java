package vn.sunvibe.listing.core.application.command;

/**
 * Start a new empty Draft on a listing (listing-publish US-2 AC-6; E06-S2 spec 3.6).
 *
 * @param dataVersion the listing's row version the caller read (listing BR-3)
 */
public record RevisionStartCommand(String listingId, long dataVersion) {
}
