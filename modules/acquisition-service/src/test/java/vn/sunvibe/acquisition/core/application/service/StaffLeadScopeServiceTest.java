package vn.sunvibe.acquisition.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.shared.LeadAccessPolicy;
import vn.sunvibe.acquisition.core.application.usecase.StaffLeadScope;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.testsupport.InMemoryOwnerLeadRepository;
import vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A staff member's lead scope as brokerage's agreement list reads it (E05-S7 spec S7-D2, SC-1; brokerage-agreement
 * BR-16): acquisition's one mapping from roles to leads, every lead status, failing closed for anyone else.
 */
class StaffLeadScopeServiceTest {

    private static final String SALE_A = "01JSALEA000000000000000000";
    private static final String SALE_B = "01JSALEB000000000000000000";

    private final InMemoryOwnerLeadRepository leads = new InMemoryOwnerLeadRepository();
    private final StaffLeadScopeService service = new StaffLeadScopeService(new LeadAccessPolicy(), leads);

    @Test
    void scopeFor_leaderManagerAdmin_allLeads() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        for (Set<String> authorities : List.of(Set.of("ROLE_SALE_LEADER"), Set.of("ROLE_SALES_MANAGER"),
                Set.of("ROLE_ADMIN"), Set.of("ROLE_SALE", "ROLE_SALE_LEADER"))) {
            StaffLeadScope scope = service.scopeFor(SALE_A, authorities);

            assertTrue(scope.allLeads(), authorities.toString());
            assertEquals(Set.of(), scope.assignedLeadIds(), authorities.toString());
        }
    }

    @Test
    void scopeFor_sale_everyLeadAssignedNowInEveryStatus() {
        OwnerLead fresh = stored(OwnerLeadStatus.NEW, SALE_A);
        OwnerLead assigned = stored(OwnerLeadStatus.ASSIGNED, SALE_A);
        OwnerLead onboarded = stored(OwnerLeadStatus.ONBOARDED, SALE_A);
        OwnerLead rejected = stored(OwnerLeadStatus.REJECTED, SALE_A);
        stored(OwnerLeadStatus.ASSIGNED, SALE_B);

        StaffLeadScope scope = service.scopeFor(SALE_A, Set.of("ROLE_SALE"));

        assertFalse(scope.allLeads());
        assertEquals(Set.of(id(fresh), id(assigned), id(onboarded), id(rejected)), scope.assignedLeadIds());
    }

    @Test
    void scopeFor_noStaffRole_noLead() {
        stored(OwnerLeadStatus.ASSIGNED, SALE_A);

        for (Set<String> authorities : List.of(Set.of("ROLE_CUSTOMER"), Set.<String>of())) {
            StaffLeadScope scope = service.scopeFor(SALE_A, authorities);

            assertFalse(scope.allLeads(), authorities.toString());
            assertEquals(Set.of(), scope.assignedLeadIds(), authorities.toString());
        }
    }

    @Test
    void staffLeadScope_allLeadsWithIds_refused() {
        assertThrows(IllegalArgumentException.class, () -> new StaffLeadScope(true, Set.of("L1")));
        assertEquals(Set.of("L1"), new StaffLeadScope(false, Set.of("L1")).assignedLeadIds());
    }

    private OwnerLead stored(OwnerLeadStatus status, String assignee) {
        OwnerLead lead = OwnerLeadFixture.lead(status, assignee, (Instant) null, null);
        leads.save(lead);
        return lead;
    }

    private static String id(OwnerLead lead) {
        return lead.getId().getValue();
    }
}
