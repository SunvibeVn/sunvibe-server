package vn.sunvibe.acquisition.core.domain.model.activity;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.lead.LeadChange;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLead;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/** A history entry built from what a lead command changed. */
class OwnerLeadActivityFromChangeTest {

    private static final String ACTOR = "01JUSERAAAAAAAAAAAAAAAAAAA";

    @Test
    void from_statusChange_copiesTheChangeAndAddsActorAndTime() {
        OwnerLead lead = lead(OwnerLeadStatus.ASSIGNED, ACTOR);
        Instant nextAction = Instant.parse("2026-12-01T09:00:00Z");
        LeadChange change = lead.changeStatus(OwnerLeadStatus.ON_HOLD, null, null, "waiting", nextAction);
        Instant at = Instant.parse("2026-10-17T10:00:00Z");

        OwnerLeadActivity activity = OwnerLeadActivity.from(42L, lead.getId(), ACTOR, change, at);

        assertEquals(42L, activity.getId());
        assertEquals(lead.getId(), activity.getLeadId());
        assertEquals(OwnerLeadActivityType.STATUS_CHANGED, activity.getType());
        assertEquals(ACTOR, activity.getActorUserId());
        assertEquals(OwnerLeadStatus.ASSIGNED, activity.getFromStatus());
        assertEquals(OwnerLeadStatus.ON_HOLD, activity.getToStatus());
        assertNull(activity.getFromAssigneeId());
        assertNull(activity.getToAssigneeId());
        assertEquals("waiting", activity.getNote());
        assertEquals(nextAction, activity.getNextActionAt());
        assertEquals(at, activity.getOccurredAt());
    }

    @Test
    void from_reassignment_copiesBothAssignees() {
        OwnerLead lead = lead(OwnerLeadStatus.ASSIGNED, ACTOR);
        LeadChange change = lead.assignTo("01JUSERBBBBBBBBBBBBBBBBBBB", "handover").orElseThrow();

        OwnerLeadActivity activity = OwnerLeadActivity.from(7L, OwnerLeadId.generate(), null, change, Instant.now());

        assertEquals(OwnerLeadActivityType.REASSIGNED, activity.getType());
        assertNull(activity.getActorUserId());
        assertEquals(ACTOR, activity.getFromAssigneeId());
        assertEquals("01JUSERBBBBBBBBBBBBBBBBBBB", activity.getToAssigneeId());
        assertEquals("handover", activity.getNote());
    }
}
