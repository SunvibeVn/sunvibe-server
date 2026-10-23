package vn.sunvibe.acquisition.core.application.shared;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadAccess;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one mapping from (already expanded) roles to lead access (acquisition-owner-lead-queue BR-1, survey-schedule spec
 * 5.4): SALE_LEADER, SALES_MANAGER and ADMIN see every lead, SALE only their own, anything else nothing.
 */
class LeadAccessPolicyTest {

    private final LeadAccessPolicy policy = new LeadAccessPolicy();

    @Test
    void accessFor_sale_isOwn() {
        assertEquals(Optional.of(LeadAccess.OWN), policy.accessFor(Set.of("ROLE_SALE")));
    }

    @Test
    void accessFor_leaderManagerAdmin_isAll() {
        for (String role : new String[]{"ROLE_SALE_LEADER", "ROLE_SALES_MANAGER", "ROLE_ADMIN"}) {
            assertEquals(Optional.of(LeadAccess.ALL), policy.accessFor(Set.of(role)), role);
            assertEquals(Optional.of(LeadAccess.ALL), policy.accessFor(Set.of(role, "ROLE_SALE")), role);
        }
    }

    @Test
    void accessFor_noStaffRole_isEmpty() {
        assertEquals(Optional.empty(), policy.accessFor(Set.of()));
        assertEquals(Optional.empty(), policy.accessFor(Set.of("ROLE_CUSTOMER", "ROLE_OWNER")));
        // Not expanded: SUPER_ADMIN alone is not ADMIN here; the caller expands the hierarchy first
        assertEquals(Optional.empty(), policy.accessFor(Set.of("ROLE_SUPER_ADMIN")));
    }
}
