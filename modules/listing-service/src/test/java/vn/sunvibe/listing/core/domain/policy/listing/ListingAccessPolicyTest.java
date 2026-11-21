package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may read and create a listing (listing BR-5, BR-16; listing-publish section 2; E06-S1 spec E06S1-D2, E06S1-D8),
 * over the expanded authorities of the caller.
 */
class ListingAccessPolicyTest {

    private static final String VIEWER = "V1";
    private static final String OTHER = "V2";

    @Test
    void mayReadListing_everyListingRoles_true() {
        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            assertTrue(ListingAccessPolicy.mayReadListing(Set.of(role), VIEWER, OTHER), role);
            assertTrue(ListingAccessPolicy.mayReadListing(Set.of(role), VIEWER, null), role);
        }
    }

    @Test
    void mayReadListing_saleOnly_onlyAsAssignee() {
        Set<String> sale = Set.of("ROLE_SALE");

        assertTrue(ListingAccessPolicy.mayReadListing(sale, VIEWER, VIEWER));
        assertFalse(ListingAccessPolicy.mayReadListing(sale, VIEWER, OTHER));
        assertFalse(ListingAccessPolicy.mayReadListing(sale, VIEWER, null));
        assertFalse(ListingAccessPolicy.mayReadListing(Set.of("ROLE_CUSTOMER"), VIEWER, VIEWER));
    }

    @Test
    void mayReadAgreementListings_reviewerOnly_false() {
        Set<String> reviewer = Set.of("ROLE_LISTING_REVIEWER");

        assertFalse(ListingAccessPolicy.mayReadAgreementListings(reviewer, VIEWER, OTHER));
        assertFalse(ListingAccessPolicy.mayReadAgreementListings(reviewer, VIEWER, VIEWER));
        assertFalse(ListingAccessPolicy.mayReadAgreementListings(reviewer, VIEWER, null));
    }

    @Test
    void mayReadAgreementListings_managersAndAssigneeSale_true() {
        for (String role : List.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN")) {
            assertTrue(ListingAccessPolicy.mayReadAgreementListings(Set.of(role), VIEWER, OTHER), role);
            assertTrue(ListingAccessPolicy.mayReadAgreementListings(Set.of(role), VIEWER, null), role);
        }
        assertTrue(ListingAccessPolicy.mayReadAgreementListings(Set.of("ROLE_SALE"), VIEWER, VIEWER));
        assertFalse(ListingAccessPolicy.mayReadAgreementListings(Set.of("ROLE_SALE"), VIEWER, OTHER));
    }

    @Test
    void mayCreate_saleOrLeaderAssignee_true() {
        assertTrue(ListingAccessPolicy.mayCreate(Set.of("ROLE_SALE"), VIEWER, VIEWER));
        assertTrue(ListingAccessPolicy.mayCreate(Set.of("ROLE_SALE_LEADER"), VIEWER, VIEWER));
        assertFalse(ListingAccessPolicy.mayCreate(Set.of("ROLE_SALE"), VIEWER, OTHER));
        assertFalse(ListingAccessPolicy.mayCreate(Set.of("ROLE_SALE_LEADER"), VIEWER, OTHER));
    }

    @Test
    void mayCreate_managerOrReviewerAssignee_false() {
        for (String role : List.of("ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER")) {
            assertFalse(ListingAccessPolicy.mayCreate(Set.of(role), VIEWER, VIEWER), role);
        }
    }

    @Test
    void mayCreate_noAssignee_false() {
        assertFalse(ListingAccessPolicy.mayCreate(Set.of("ROLE_SALE"), VIEWER, null));
        assertFalse(ListingAccessPolicy.mayCreate(Set.of("ROLE_SALE_LEADER"), VIEWER, null));
    }
}
