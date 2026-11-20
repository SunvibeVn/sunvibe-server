package vn.sunvibe.listing.core.application.query;

import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.TransactionType;

import java.time.Instant;
import java.util.List;

/**
 * A listing as staff read it (listing-publish E06-S1 spec section 4, E06S1-D5, E06S1-D17): nothing of E05's answer.
 *
 * @param state                the stored state, or {@code ARCHIVED} when the agreement ended
 * @param dataVersion          the row version, for the commands of later slices (A-2)
 * @param viewerIsAssignedSale the viewer holds the agreement's basis lead now; a hint, the commands decide (A-3)
 * @param revisions            at most the 20 newest revisions, newest first
 * @param hasOlderRevisions    the listing has older revisions than those answered
 * @param openRevisionNoOrNull the number of its Draft or Pending review revision, if it has one (E06-S2 spec
 *                             E06S2-D20)
 */
public record ListingView(String listingId, String code, ListingState state, TransactionType transactionType,
                          String unitId, String agreementId, Instant createdAt, long dataVersion,
                          boolean viewerIsAssignedSale, List<Revision> revisions, boolean hasOlderRevisions,
                          Integer openRevisionNoOrNull) {

    public ListingView {
        revisions = List.copyOf(revisions);
    }

    /** One revision: its number, stored status and creation time; no creator, editor or reviewer. */
    public record Revision(int revisionNo, RevisionStatus status, Instant createdAt) {
    }
}
