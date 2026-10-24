package vn.sunvibe.acquisition.core.domain.model.activity;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** History entries of survey checklists (survey-checklist BR-15, spec A-13). */
class OwnerLeadActivityTest {

    private static final OwnerLeadId LEAD = OwnerLeadId.of("01JLEADAAAAAAAAAAAAAAAAAAA");
    private static final String ACTOR = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final String CHECKLIST = "01JCHECKLISTAAAAAAAAAAAAAA";
    private static final Instant AT = Instant.parse("2026-10-24T03:00:00Z");

    @Test
    void checklistEvent_saved_carriesReferenceAndDetail() {
        OwnerLeadActivity activity = OwnerLeadActivity.checklistEvent(7L, LEAD, OwnerLeadActivityType.CHECKLIST_SAVED,
                ACTOR, CHECKLIST, 2, "DIMENSIONS", AT);

        assertEquals(7L, activity.getId());
        assertEquals(LEAD, activity.getLeadId());
        assertEquals(OwnerLeadActivityType.CHECKLIST_SAVED, activity.getType());
        assertEquals(ACTOR, activity.getActorUserId());
        assertEquals(CHECKLIST, activity.getReferenceId());
        assertEquals(2, activity.getReferenceNo());
        assertEquals("DIMENSIONS", activity.getReferenceDetail());
        assertEquals(AT, activity.getOccurredAt());
        assertNull(activity.getNote());
        assertNull(activity.getFromStatus());
        assertNull(activity.getToStatus());
        assertNull(activity.getFromAssigneeId());
        assertNull(activity.getToAssigneeId());
        assertNull(activity.getNextActionAt());
    }

    @Test
    void checklistEvent_startedAndTakenOver_haveNoDetail() {
        assertNull(OwnerLeadActivity.checklistEvent(8L, LEAD, OwnerLeadActivityType.CHECKLIST_STARTED, ACTOR,
                CHECKLIST, 1, null, AT).getReferenceDetail());
        assertNull(OwnerLeadActivity.checklistEvent(9L, LEAD, OwnerLeadActivityType.CHECKLIST_TAKEN_OVER, ACTOR,
                CHECKLIST, 1, null, AT).getReferenceDetail());
    }

    @Test
    void checklistEvent_otherType_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> OwnerLeadActivity.checklistEvent(7L, LEAD,
                OwnerLeadActivityType.NOTE_ADDED, ACTOR, CHECKLIST, 1, null, AT));
    }

    @Test
    void checklistEvent_startedWithDetail_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> OwnerLeadActivity.checklistEvent(7L, LEAD,
                OwnerLeadActivityType.CHECKLIST_STARTED, ACTOR, CHECKLIST, 1, "DIMENSIONS", AT));
    }

    @Test
    void checklistEvent_savedWithoutDetail_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> OwnerLeadActivity.checklistEvent(7L, LEAD,
                OwnerLeadActivityType.CHECKLIST_SAVED, ACTOR, CHECKLIST, 1, null, AT));
    }

    @Test
    void load_detailWithoutReference_isRefused() {
        assertThrows(IllegalArgumentException.class, () -> OwnerLeadActivity.load(7L, LEAD,
                OwnerLeadActivityType.CHECKLIST_SAVED, ACTOR, null, null, null, null, null, null, AT, null, null,
                "DIMENSIONS"));
    }
}
