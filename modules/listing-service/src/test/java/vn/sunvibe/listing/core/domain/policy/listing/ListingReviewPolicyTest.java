package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who may decide a revision (listing-publish BR-7, US-4 AC-4, E-6; listing BR-16; E06-S4 spec E06S4-D2 to D5; RV-2
 * policy half): a reviewer role, none of the authors, not the assigned Sale whatever roles, a Pending review revision of
 * a listing not archived; every reason in contract order.
 */
class ListingReviewPolicyTest {

    private static final Set<String> REVIEWER = Set.of("ROLE_LISTING_REVIEWER");
    private static final Set<String> ADMIN = Set.of("ROLE_ADMIN", "ROLE_SALE_LEADER", "ROLE_SALES_MANAGER");
    private static final Set<String> MANAGER = Set.of("ROLE_SALES_MANAGER", "ROLE_SALE_LEADER", "ROLE_SALE");

    @Test
    void ineligibility_reviewerNeitherAuthorNorAssignee_empty() {
        assertEquals(List.of(), ListingReviewPolicy.ineligibility(REVIEWER, "R1", "S1", Set.of("S1", "S2"),
                RevisionStatus.PENDING_REVIEW, false));
        assertEquals(List.of(), ListingReviewPolicy.ineligibility(ADMIN, "A1", null, Set.of(),
                RevisionStatus.PENDING_REVIEW, false));
    }

    @Test
    void ineligibility_everyReason_contractOrder() {
        Set<String> saleAlone = Set.of("ROLE_SALE");

        assertEquals(List.of(ReviewIneligibility.NOT_A_REVIEWER, ReviewIneligibility.AUTHOR,
                        ReviewIneligibility.ASSIGNED_SALE, ReviewIneligibility.NOT_PENDING,
                        ReviewIneligibility.LISTING_ARCHIVED),
                ListingReviewPolicy.ineligibility(saleAlone, "S1", "S1", Set.of("S1"), RevisionStatus.NEEDS_CHANGES,
                        true));
        assertEquals(List.of(ReviewIneligibility.AUTHOR, ReviewIneligibility.NOT_PENDING),
                ListingReviewPolicy.ineligibility(REVIEWER, "R1", "S1", Set.of("R1"), RevisionStatus.CANCELLED, false));
        assertEquals(List.of(ReviewIneligibility.LISTING_ARCHIVED),
                ListingReviewPolicy.ineligibility(REVIEWER, "R1", null, Set.of("S1"), RevisionStatus.PENDING_REVIEW,
                        true));
        for (RevisionStatus status : List.of(RevisionStatus.DRAFT, RevisionStatus.APPROVED, RevisionStatus.REJECTED)) {
            assertEquals(List.of(ReviewIneligibility.NOT_PENDING),
                    ListingReviewPolicy.ineligibility(REVIEWER, "R1", "S1", Set.of("S1"), status, false), status.name());
        }
    }

    @Test
    void ineligibility_adminWhoIsAssignee_assignedSale() {
        assertEquals(List.of(ReviewIneligibility.ASSIGNED_SALE),
                ListingReviewPolicy.ineligibility(ADMIN, "A1", "A1", Set.of("S1"), RevisionStatus.PENDING_REVIEW,
                        false));
        assertEquals(List.of(ReviewIneligibility.AUTHOR, ReviewIneligibility.ASSIGNED_SALE),
                ListingReviewPolicy.ineligibility(REVIEWER, "R1", "R1", Set.of("R1"), RevisionStatus.PENDING_REVIEW,
                        false));
    }

    @Test
    void ineligibility_managerWithoutReviewerRole_notAReviewer() {
        assertEquals(List.of(ReviewIneligibility.NOT_A_REVIEWER),
                ListingReviewPolicy.ineligibility(MANAGER, "M1", "S1", Set.of("S1"), RevisionStatus.PENDING_REVIEW,
                        false));
        assertEquals(List.of(ReviewIneligibility.NOT_A_REVIEWER),
                ListingReviewPolicy.ineligibility(Set.of(), "M1", null, Set.of(), RevisionStatus.PENDING_REVIEW,
                        false));
    }
}
