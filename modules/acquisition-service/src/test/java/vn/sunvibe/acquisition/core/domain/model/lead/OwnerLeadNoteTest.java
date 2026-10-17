package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.*;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.LONG_AGO;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/** Notes on a lead (Q9-9, Q9-7). */
class OwnerLeadNoteTest {

    private static final String A = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final Instant NEXT_ACTION = Instant.parse("2026-11-01T09:00:00Z");

    @Test
    void addNote_withoutNextAction_changesNoLeadField() {
        OwnerLead lead = lead(CONTACTED, A, NEXT_ACTION, 3L);

        LeadChange change = lead.addNote("  called the owner  ", null);

        assertEquals(OwnerLeadActivityType.NOTE_ADDED, change.type());
        assertEquals("called the owner", change.note());
        assertNull(change.nextActionAt());
        assertNull(change.fromStatus());
        assertNull(change.toStatus());
        assertEquals(CONTACTED, lead.getStatus());
        assertEquals(A, lead.getAssignedUserId());
        assertEquals(NEXT_ACTION, lead.getNextActionAt());
        assertEquals(LONG_AGO, lead.getUpdatedAt());
        assertEquals(3L, lead.getVersion());
    }

    @Test
    void addNote_withNextAction_updatesIt() {
        OwnerLead lead = lead(CONTACTED, A, NEXT_ACTION, 3L);
        Instant later = Instant.parse("2026-12-01T09:00:00Z");

        LeadChange change = lead.addNote("call again", later);

        assertEquals(later, lead.getNextActionAt());
        assertEquals(later, change.nextActionAt());
        assertTrue(lead.getUpdatedAt().isAfter(LONG_AGO));
        assertEquals(3L, lead.getVersion());
    }

    @Test
    void addNote_newLead_isAllowed() {
        assertEquals("seen", lead(NEW, null).addNote("seen", null).note());
    }

    @Test
    void addNote_multiLineNote_keepsInnerNewlinesAndSpacesInNfc() {
        LeadChange change = lead(CONTACTED, A).addNote("  Line one\n  cafe\u0301   two\n\nend  ", null);

        assertEquals("Line one\n  caf\u00e9   two\n\nend", change.note());
    }

    @Test
    void addNote_2001CharsThatAre2000InNfc_succeeds() {
        LeadChange change = lead(CONTACTED, A).addNote("x".repeat(1999) + "e\u0301", null);

        assertEquals(2000, change.note().length());
    }

    @Test
    void addNote_blankOrOver2000_throwsOnNote() {
        OwnerLead lead = lead(CONTACTED, A);

        for (String note : new String[]{null, "", "   ", "x".repeat(2001)}) {
            InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                    () -> lead.addNote(note, null));
            assertEquals("note", ex.getField());
        }
    }

    @Test
    void addNote_exactly2000_succeeds() {
        assertEquals(2000, lead(CONTACTED, A).addNote("x".repeat(2000), null).note().length());
    }

    @Test
    void addNote_closedLead_throws() {
        for (OwnerLeadStatus closed : List.of(DUPLICATE, REJECTED, WITHDRAWN)) {
            OwnerLead lead = lead(closed, A);

            InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                    () -> lead.addNote("late note", NEXT_ACTION));

            assertEquals(closed, ex.getFrom());
            assertNull(ex.getTo());
            assertEquals(List.of(), ex.getAllowed());
            assertNull(lead.getNextActionAt());
        }
    }
}
