package vn.sunvibe.listing.core.domain.repository;

import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;

import java.time.Instant;

/**
 * A Pending review revision of a listing not stored as archived (listing-publish E06-S4 spec E06S4-D10): the row of the
 * review queue before its agreement's standing decides whether it is listed. Also the keyset cursor of the next chunk:
 * its submission time and revision id. The title is unpublished text: {@link #toString()} names the ids only.
 */
public record ReviewCandidate(String listingId, String code, String agreementId, String revisionId, int revisionNo,
                              Instant submittedAt, String title, UnitSnapshot unitOrNull) {

    @Override
    public String toString() {
        return "ReviewCandidate[" + listingId + ", " + revisionId + "]";
    }
}
