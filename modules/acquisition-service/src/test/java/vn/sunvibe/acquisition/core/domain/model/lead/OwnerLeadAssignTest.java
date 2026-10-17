package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.*;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.LONG_AGO;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/** Assigning and reassigning a lead (Q9-6, Q9-5 domain part). */
class OwnerLeadAssignTest {

    private static final String A = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final String B = "01JUSERBBBBBBBBBBBBBBBBBBB";
    private static final Instant NEXT_ACTION = Instant.parse("2026-11-01T09:00:00Z");

    @Test
    void assignTo_newLead_becomesAssignedWithAssignedActivity() {
        OwnerLead lead = lead(NEW, null);

        LeadChange change = lead.assignTo(A, null).orElseThrow();

        assertEquals(ASSIGNED, lead.getStatus());
        assertEquals(A, lead.getAssignedUserId());
        assertEquals(OwnerLeadActivityType.ASSIGNED, change.type());
        assertEquals(NEW, change.fromStatus());
        assertEquals(ASSIGNED, change.toStatus());
        assertNull(change.fromAssigneeId());
        assertEquals(A, change.toAssigneeId());
        assertTrue(lead.getUpdatedAt().isAfter(LONG_AGO));
        assertEquals(3L, lead.getVersion());
    }

    @Test
    void assignTo_assignedLead_isReassignedKeepingStatusNextActionAndNotes() {
        OwnerLead lead = lead(ASSIGNED, A, NEXT_ACTION, 3L);

        LeadChange change = lead.assignTo(B, null).orElseThrow();

        assertEquals(ASSIGNED, lead.getStatus());
        assertEquals(B, lead.getAssignedUserId());
        assertEquals(NEXT_ACTION, lead.getNextActionAt());
        assertEquals("Owner note", lead.getNotes());
        assertEquals(OwnerLeadActivityType.REASSIGNED, change.type());
        assertEquals(A, change.fromAssigneeId());
        assertEquals(B, change.toAssigneeId());
        assertNull(change.fromStatus());
        assertNull(change.toStatus());
    }

    @Test
    void assignTo_contactedLead_keepsContactedStatus() {
        OwnerLead lead = lead(CONTACTED, A);

        LeadChange change = lead.assignTo(B, null).orElseThrow();

        assertEquals(CONTACTED, lead.getStatus());
        assertEquals(B, lead.getAssignedUserId());
        assertEquals(OwnerLeadActivityType.REASSIGNED, change.type());
    }

    @Test
    void assignTo_onHoldLead_keepsOnHoldStatus() {
        OwnerLead lead = lead(ON_HOLD, A);

        lead.assignTo(B, null);

        assertEquals(ON_HOLD, lead.getStatus());
        assertEquals(B, lead.getAssignedUserId());
    }

    @Test
    void assignTo_sameAssignee_returnsEmptyAndChangesNothing() {
        OwnerLead lead = lead(ASSIGNED, A);

        Optional<LeadChange> change = lead.assignTo(A, null);

        assertTrue(change.isEmpty());
        assertEquals(A, lead.getAssignedUserId());
        assertEquals(LONG_AGO, lead.getUpdatedAt());
        assertEquals(3L, lead.getVersion());
    }

    @Test
    void assignTo_closedLead_throwsWithNullTo() {
        for (OwnerLeadStatus closed : List.of(DUPLICATE, REJECTED, WITHDRAWN)) {
            OwnerLead lead = lead(closed, A);

            InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                    () -> lead.assignTo(B, null));

            assertEquals(closed, ex.getFrom());
            assertNull(ex.getTo());
            assertEquals(List.of(), ex.getAllowed());
            assertEquals(A, lead.getAssignedUserId());
        }
    }

    @Test
    void assignTo_newLeadWithNote_carriesTheTrimmedNoteInTheChangeOnly() {
        OwnerLead lead = lead(NEW, null);

        LeadChange change = lead.assignTo(A, "  covers   the  ward \n now ").orElseThrow();

        assertEquals("covers   the  ward \n now", change.note());
        assertEquals("Owner note", lead.getNotes());
    }

    @Test
    void assignTo_multiLineNote_keepsInnerNewlinesAndSpacesInNfc() {
        LeadChange change = lead(ASSIGNED, A).assignTo(B, "  Line one\n  cafe\u0301   two\n\nend  ").orElseThrow();

        assertEquals("Line one\n  caf\u00e9   two\n\nend", change.note());
    }

    @Test
    void assignTo_reassignWithNote_carriesTheNoteInTheChange() {
        LeadChange change = lead(ASSIGNED, A).assignTo(B, "leaver").orElseThrow();

        assertEquals(OwnerLeadActivityType.REASSIGNED, change.type());
        assertEquals("leaver", change.note());
    }

    @Test
    void assignTo_nullOrBlankNote_meansNoNote() {
        for (String note : new String[]{null, "", "   ", " \t\n "}) {
            LeadChange change = lead(ASSIGNED, A).assignTo(B, note).orElseThrow();
            assertNull(change.note());
        }
    }

    @Test
    void assignTo_noteOf2000Chars_isAccepted() {
        LeadChange change = lead(ASSIGNED, A).assignTo(B, "x".repeat(2000)).orElseThrow();

        assertEquals(2000, change.note().length());
    }

    @Test
    void assignTo_noteOver2000Chars_throwsOnNoteAndChangesNothing() {
        OwnerLead lead = lead(ASSIGNED, A);

        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> lead.assignTo(B, "secret-note-" + "x".repeat(2000)));

        assertEquals("note", ex.getField());
        assertFalse(ex.getMessage().contains("secret-note"));
        assertEquals(A, lead.getAssignedUserId());
        assertEquals(LONG_AGO, lead.getUpdatedAt());
    }

    @Test
    void assignTo_sameAssigneeWithNote_isStillANoOp() {
        OwnerLead lead = lead(ASSIGNED, A);

        Optional<LeadChange> change = lead.assignTo(A, "ignored");

        assertTrue(change.isEmpty());
        assertEquals(LONG_AGO, lead.getUpdatedAt());
        assertEquals("Owner note", lead.getNotes());
    }

    @Test
    void assignTo_closedLeadWithNote_stillThrowsTheTransitionError() {
        assertThrows(InvalidLeadTransitionException.class, () -> lead(WITHDRAWN, A).assignTo(B, "late"));
    }

    @Test
    void assignTo_closedLeadToSameAssignee_stillThrows() {
        OwnerLead lead = lead(REJECTED, A);

        assertThrows(InvalidLeadTransitionException.class, () -> lead.assignTo(A, null));
    }
}
