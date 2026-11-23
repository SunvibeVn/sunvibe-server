package vn.sunvibe.listing.core.application.command;

/**
 * Put an Approved revision live (listing-publish US-5 AC-1, AC-3; E06-S6 spec E06S6-D5).
 *
 * @param revisionNoOrNull the revision named in the path; null when the path did not name a well-formed number
 * @param dataVersion      the listing's row version the caller read (listing BR-3)
 */
public record PutLiveCommand(String listingId, Integer revisionNoOrNull, long dataVersion) {
}
