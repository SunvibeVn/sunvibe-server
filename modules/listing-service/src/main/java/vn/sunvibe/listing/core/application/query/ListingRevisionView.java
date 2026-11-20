package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * One revision as staff read it (listing-publish E06-S2 spec E06S2-D14): its content, terms snapshot, unit snapshot and
 * submission time, the listing's data version, and for a Draft the problems its submission would answer now. No user
 * id. Unpublished text and E05's terms (US-10 AC-4, brokerage-agreement BR-17): its text names none of them.
 *
 * @param problems the violations a submission would answer now, in order; empty unless the revision is a Draft
 */
public record ListingRevisionView(String listingId, int revisionNo, RevisionStatus status, Instant createdAt,
                                  Instant updatedAt, RevisionContent content, RevisionTerms termsOrNull,
                                  UnitSnapshot unitSnapshotOrNull, Instant submittedAtOrNull, long dataVersion,
                                  List<RevisionViolation> problems) {

    public ListingRevisionView {
        problems = List.copyOf(problems);
    }

    @Override
    public String toString() {
        return "ListingRevisionView[hidden]";
    }
}
