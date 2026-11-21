package vn.sunvibe.listing.core.application.command;

/**
 * Take a Draft's terms snapshot from one of E05's versions (listing-publish US-2 AC-2, BR-4; E06-S2 spec 3.3,
 * E06S2-D6).
 *
 * @param revisionNoOrNull the revision named in the path; null when the path did not name a well-formed number
 * @param dataVersion      the listing's row version the caller read (listing BR-3)
 * @param source           {@code IN_EFFECT} or {@code PENDING_START}, as the edge checked it
 */
public record TermsCaptureCommand(String listingId, Integer revisionNoOrNull, long dataVersion, String source) {
}
