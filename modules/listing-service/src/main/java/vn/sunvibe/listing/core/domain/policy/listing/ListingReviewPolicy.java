package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Who may decide a revision now (listing-publish BR-7, US-4 AC-4, E-6, E-11; listing BR-16; E06-S4 spec E06S4-D2 to D5):
 * a listing reviewer (LISTING_REVIEWER or ADMIN in the expanded authorities) who is none of the revision's authors and
 * not the agreement's current assigned Sale, whatever roles they hold, on a Pending review revision of a listing not
 * archived. Every reason that applies is answered, in {@link ReviewIneligibility} order; none means the caller may
 * decide. Pure: the caller reads the authors (E06S4-D3) and the assignee (E06S4-D4) first.
 */
public final class ListingReviewPolicy {

    private ListingReviewPolicy() {
    }

    /** The reasons the viewer may not decide the revision, in contract order; empty when they may. */
    public static List<ReviewIneligibility> ineligibility(Set<String> authorities, String viewerId,
                                                          String assigneeIdOrNull, Set<String> authors,
                                                          RevisionStatus status, boolean archived) {
        List<ReviewIneligibility> reasons = new ArrayList<>();
        if (!ListingAccessPolicy.holdsReviewerRole(authorities)) {
            reasons.add(ReviewIneligibility.NOT_A_REVIEWER);
        }
        if (viewerId != null && authors != null && authors.contains(viewerId)) {
            reasons.add(ReviewIneligibility.AUTHOR);
        }
        if (viewerId != null && viewerId.equals(assigneeIdOrNull)) {
            reasons.add(ReviewIneligibility.ASSIGNED_SALE);
        }
        if (status != RevisionStatus.PENDING_REVIEW) {
            reasons.add(ReviewIneligibility.NOT_PENDING);
        }
        if (archived) {
            reasons.add(ReviewIneligibility.LISTING_ARCHIVED);
        }
        return List.copyOf(reasons);
    }
}
