package vn.sunvibe.listing.core.application.query;

/**
 * What the freshness record command returns (listing-publish E06-S3 spec section 4, {@code ListingFreshnessRecorded}):
 * the listing's new data version (listing BR-3) and whether the contact is a valid confirmation (BR-9).
 */
public record FreshnessRecordResult(String listingId, long dataVersion, boolean valid) {
}
