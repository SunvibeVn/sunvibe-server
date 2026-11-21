package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;

/**
 * A listing that may need reconfirmation (listing-publish E06-S3 spec E06S3-D7): not stored as archived, with a valid
 * confirmation on or before the date asked; its agreement's standing decides whether it is listed. Also the keyset
 * cursor of the next chunk: its confirmation date and id.
 */
public record ReconfirmationCandidate(String listingId, String code, String agreementId, ListingState storedState,
                                      FreshnessSummary summary) {
}
