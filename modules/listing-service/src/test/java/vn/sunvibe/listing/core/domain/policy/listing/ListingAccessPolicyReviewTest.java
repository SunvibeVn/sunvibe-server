package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who reads the review queue and reviews, and who holds a reviewer role (listing-publish E06-S4 spec E06S4-D2): the four
 * roles that read every listing read them; LISTING_REVIEWER and ADMIN decide, a manager only when also a reviewer.
 */
class ListingAccessPolicyReviewTest {

    @Test
    void mayReadReviews_fourRoles_true() {
        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            assertTrue(ListingAccessPolicy.mayReadReviews(Set.of(role)), role);
        }
    }

    @Test
    void mayReadReviews_saleOrCustomer_false() {
        assertFalse(ListingAccessPolicy.mayReadReviews(Set.of("ROLE_SALE")));
        assertFalse(ListingAccessPolicy.mayReadReviews(Set.of("ROLE_CUSTOMER")));
        assertFalse(ListingAccessPolicy.mayReadReviews(Set.of()));
        assertFalse(ListingAccessPolicy.mayReadReviews(null));
    }

    @Test
    void holdsReviewerRole_reviewerOrAdmin_true() {
        assertTrue(ListingAccessPolicy.holdsReviewerRole(Set.of("ROLE_LISTING_REVIEWER")));
        assertTrue(ListingAccessPolicy.holdsReviewerRole(Set.of("ROLE_ADMIN")));
        assertTrue(ListingAccessPolicy.holdsReviewerRole(Set.of("ROLE_SALES_MANAGER", "ROLE_LISTING_REVIEWER")));
    }

    @Test
    void holdsReviewerRole_managersAndSale_false() {
        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_SALE", "ROLE_CUSTOMER")) {
            assertFalse(ListingAccessPolicy.holdsReviewerRole(Set.of(role)), role);
        }
        assertFalse(ListingAccessPolicy.holdsReviewerRole(null));
    }
}
