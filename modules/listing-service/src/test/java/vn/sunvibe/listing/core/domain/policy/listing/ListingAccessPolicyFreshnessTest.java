package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Who records a freshness contact and who sees every listing to reconfirm (listing BR-16; listing-publish section 2;
 * E06-S3 spec E06S3-D2): the current assignee holding SALE or SALE_LEADER records; SALE_LEADER, SALES_MANAGER and ADMIN
 * see every listing to reconfirm, over the caller's expanded authorities.
 */
class ListingAccessPolicyFreshnessTest {

    private static final String VIEWER = "V1";
    private static final String OTHER = "V2";

    @Test
    void mayRecordFreshness_saleOrLeaderAssignee_true() {
        assertEquals(List.of(true, true), List.of(
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_SALE"), VIEWER, VIEWER),
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_SALE_LEADER", "ROLE_SALE"), VIEWER, VIEWER)));
    }

    @Test
    void mayRecordFreshness_managerAdminReviewerOrNotAssignee_false() {
        assertEquals(List.of(false, false, false, false, false), List.of(
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_SALES_MANAGER"), VIEWER, VIEWER),
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_ADMIN"), VIEWER, VIEWER),
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_LISTING_REVIEWER"), VIEWER, VIEWER),
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_SALE"), VIEWER, OTHER),
                ListingAccessPolicy.mayRecordFreshness(Set.of("ROLE_SALE"), VIEWER, null)));
    }

    @Test
    void seesEveryReconfirmation_leaderManagerAdmin_trueSaleReviewer_false() {
        assertEquals(List.of(true, true, true), List.of(
                ListingAccessPolicy.seesEveryReconfirmation(Set.of("ROLE_SALE_LEADER", "ROLE_SALE")),
                ListingAccessPolicy.seesEveryReconfirmation(Set.of("ROLE_SALES_MANAGER")),
                ListingAccessPolicy.seesEveryReconfirmation(Set.of("ROLE_ADMIN"))));
        assertFalse(ListingAccessPolicy.seesEveryReconfirmation(Set.of("ROLE_SALE")));
        assertFalse(ListingAccessPolicy.seesEveryReconfirmation(Set.of("ROLE_LISTING_REVIEWER")));
    }
}
