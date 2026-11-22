package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who puts a revision live (listing BR-16; listing-publish E06-S6 spec E06S6-D2; PL-5 rule half): only a SALE or
 * SALE_LEADER who is the current assignee of the agreement's basis lead, the rule of drafting; managers, reviewers and
 * customers never, whatever they hold.
 */
class ListingAccessPolicyPutLiveTest {

    @Test
    void mayPutLive_saleOrLeaderAssignee_true() {
        assertTrue(ListingAccessPolicy.mayPutLive(Set.of("ROLE_SALE"), "S1", "S1"));
        assertTrue(ListingAccessPolicy.mayPutLive(Set.of("ROLE_SALE_LEADER", "ROLE_SALE"), "L1", "L1"));
    }

    @Test
    void mayPutLive_otherRoleOrNotAssignee_false() {
        for (String role : List.of("ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER", "ROLE_CUSTOMER")) {
            assertFalse(ListingAccessPolicy.mayPutLive(Set.of(role), "S1", "S1"), role);
        }
        assertFalse(ListingAccessPolicy.mayPutLive(Set.of("ROLE_SALE"), "S2", "S1"), "not the assignee");
        assertFalse(ListingAccessPolicy.mayPutLive(Set.of("ROLE_SALE"), "S1", null), "no assignee");
        assertFalse(ListingAccessPolicy.mayPutLive(Set.of(), "S1", "S1"), "no role");
    }
}
