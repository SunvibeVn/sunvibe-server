package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;

/**
 * What a revision command returns (listing-publish E06-S2 spec section 4, {@code ListingRevisionChange}): the revision it
 * acted on, its status now and the listing's new data version (listing BR-3).
 */
public record RevisionCommandResult(String listingId, int revisionNo, RevisionStatus status, long dataVersion) {
}
