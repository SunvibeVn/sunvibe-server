package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.ASSIGNED;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.NEW;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/** Who may see a lead (Q9-5; acquisition-owner-lead-queue BR-1). */
class LeadViewerTest {

    private static final String A = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final String B = "01JUSERBBBBBBBBBBBBBBBBBBB";

    @Test
    void canSee_all_seesEveryLead() {
        LeadViewer viewer = new LeadViewer(B, LeadAccess.ALL);

        assertTrue(viewer.canSee(lead(ASSIGNED, A)));
        assertTrue(viewer.canSee(lead(NEW, null)));
    }

    @Test
    void canSee_own_seesOnlyAssignedToThem() {
        LeadViewer viewer = new LeadViewer(A, LeadAccess.OWN);

        assertTrue(viewer.canSee(lead(ASSIGNED, A)));
        assertFalse(viewer.canSee(lead(ASSIGNED, B)));
    }

    @Test
    void canSee_own_neverSeesAnUnassignedLead() {
        assertFalse(new LeadViewer(A, LeadAccess.OWN).canSee(lead(NEW, null)));
    }

    @Test
    void canSee_own_afterReassignment_isFalse() {
        OwnerLead lead = lead(ASSIGNED, A);
        LeadViewer viewer = new LeadViewer(A, LeadAccess.OWN);
        assertTrue(viewer.canSee(lead));

        lead.assignTo(B, null);

        assertFalse(viewer.canSee(lead));
        assertTrue(new LeadViewer(B, LeadAccess.OWN).canSee(lead));
    }
}
