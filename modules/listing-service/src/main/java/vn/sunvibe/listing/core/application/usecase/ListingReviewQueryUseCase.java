package vn.sunvibe.listing.core.application.usecase;

import vn.sunvibe.listing.core.application.query.ReviewQueueView;
import vn.sunvibe.listing.core.application.query.RevisionDecisionView;
import vn.sunvibe.listing.core.application.query.RevisionReviewView;

/**
 * Use Case - the review's reads (listing-publish US-4 AC-1, AC-2, AC-6; E06-S4 spec sections 3.1, 3.2, 3.5): the queue,
 * the review read of one revision, and the decision read. Read-only. The caller's role is the controller's gate; the
 * scope is checked here, one "not found" for anything the caller may not read (listing BR-5).
 */
public interface ListingReviewQueryUseCase {

    /** At most 50 Pending review revisions, oldest first, each with the viewer's eligibility. */
    ReviewQueueView queue(ListingViewer viewer);

    /** The review context of one revision of any status; never the real location. */
    RevisionReviewView review(String listingId, Integer revisionNoOrNull, ListingViewer viewer);

    /** The decision of one revision, for whoever reads the listing. */
    RevisionDecisionView decision(String listingId, Integer revisionNoOrNull, ListingViewer viewer);
}
