package vn.sunvibe.listing.core.domain.policy.listing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who drafts a listing's revisions (listing BR-16; listing-publish section 2 "Không soạn thay"; E06-S2 spec E06S2-D2):
 * the current assignee holding SALE or SALE_LEADER, over the caller's expanded authorities. The new cases of
 * {@link ListingAccessPolicy} beside {@code ListingAccessPolicyTest} (plan E06S2-P1).
 */
class ListingAccessPolicyDraftTest {

    private static final String VIEWER = "V1";
    private static final String OTHER = "V2";

    @Test
    void mayDraft_saleOrLeaderAssignee_true() {
        assertTrue(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE"), VIEWER, VIEWER));
        assertTrue(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE_LEADER", "ROLE_SALE"), VIEWER, VIEWER));
        assertTrue(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE_LEADER"), VIEWER, VIEWER));
    }

    @Test
    void mayDraft_saleOrLeaderNotAssignee_false() {
        assertFalse(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE"), VIEWER, OTHER));
        assertFalse(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE_LEADER", "ROLE_SALE"), VIEWER, OTHER));
    }

    @Test
    void mayDraft_managerAdminOrReviewerAssignee_false() {
        for (String role : List.of("ROLE_SALES_MANAGER", "ROLE_ADMIN", "ROLE_LISTING_REVIEWER", "ROLE_SUPER_ADMIN",
                "ROLE_CUSTOMER", "ROLE_LEADER", "ROLE_MANAGER")) {
            assertFalse(ListingAccessPolicy.mayDraft(Set.of(role), VIEWER, VIEWER), role);
        }
        assertFalse(ListingAccessPolicy.mayDraft(null, VIEWER, VIEWER));
    }

    @Test
    void mayDraft_noAssignee_false() {
        assertFalse(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE"), VIEWER, null));
        assertFalse(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE_LEADER"), VIEWER, null));
        assertFalse(ListingAccessPolicy.mayDraft(Set.of("ROLE_SALE"), null, null));
    }
}
