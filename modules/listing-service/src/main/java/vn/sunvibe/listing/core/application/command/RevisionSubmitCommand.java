package vn.sunvibe.listing.core.application.command;

/**
 * Submit a Draft for review (listing-publish US-2 AC-4, AC-5; E06-S2 spec 3.4).
 *
 * @param revisionNoOrNull the revision named in the path; null when the path did not name a well-formed number
 * @param dataVersion      the listing's row version the caller read (listing BR-3)
 */
public record RevisionSubmitCommand(String listingId, Integer revisionNoOrNull, long dataVersion) {
}
