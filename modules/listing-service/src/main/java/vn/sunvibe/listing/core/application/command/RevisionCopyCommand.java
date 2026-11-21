package vn.sunvibe.listing.core.application.command;

/**
 * Copy a Needs changes revision into a new Draft (listing-publish US-2 AC-6; E06-S4 spec 3.6, E06S4-D14).
 *
 * @param revisionNoOrNull the source revision named in the path; null when the path did not name a well-formed number
 * @param dataVersion      the listing's row version the caller read (listing BR-3)
 */
public record RevisionCopyCommand(String listingId, Integer revisionNoOrNull, long dataVersion) {
}
