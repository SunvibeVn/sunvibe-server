package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadCommandException;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.*;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.LONG_AGO;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/**
 * Status changes of a lead: the transition table of acquisition module brief BR-3 (Q9-7), closing rules (Q9-8).
 */
class OwnerLeadStatusChangeTest {

    private static final String ASSIGNEE = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final Instant NEXT_ACTION = Instant.parse("2026-11-01T09:00:00Z");
    private static final List<OwnerLeadStatus> CLOSING = List.of(DUPLICATE, REJECTED, WITHDRAWN);

    /** The table of acquisition module brief BR-3, written out per source state. */
    private static final Map<OwnerLeadStatus, List<OwnerLeadStatus>> TABLE = Map.of(
            NEW, CLOSING,
            ASSIGNED, List.of(CONTACTED, ON_HOLD, DUPLICATE, REJECTED, WITHDRAWN),
            CONTACTED, List.of(ON_HOLD, DUPLICATE, REJECTED, WITHDRAWN),
            ON_HOLD, List.of(CONTACTED, DUPLICATE, REJECTED, WITHDRAWN));

    private static List<OwnerLeadStatus> tableFor(OwnerLeadStatus from) {
        return TABLE.getOrDefault(from, List.of());
    }

    @Test
    void allowedNextStatuses_newLead_isTheThreeClosingStates() {
        assertEquals(CLOSING, lead(NEW, null).allowedNextStatuses());
    }

    @Test
    void changeStatus_everyPair_matchesTheBr3Table() {
        for (OwnerLeadStatus from : OwnerLeadStatus.values()) {
            for (OwnerLeadStatus to : OwnerLeadStatus.values()) {
                OwnerLead lead = lead(from, ASSIGNEE);
                String pair = from + " -> " + to;
                assertEquals(tableFor(from), lead.allowedNextStatuses(), "allowed of " + from);

                if (tableFor(from).contains(to)) {
                    LeadChange change = lead.changeStatus(to, "reason", OwnerLeadId.generate(), null, null);
                    assertEquals(to, lead.getStatus(), pair);
                    assertEquals(OwnerLeadActivityType.STATUS_CHANGED, change.type(), pair);
                } else {
                    InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                            () -> lead.changeStatus(to, "reason", OwnerLeadId.generate(), null, null), pair);
                    assertEquals(from, ex.getFrom(), pair);
                    assertEquals(to, ex.getTo(), pair);
                    assertEquals(tableFor(from), ex.getAllowed(), pair);
                    assertEquals(from, lead.getStatus(), pair);
                }
            }
        }
    }

    @Test
    void allowedNextStatuses_closedAndUnusedStates_isEmpty() {
        for (OwnerLeadStatus status : List.of(DUPLICATE, REJECTED, WITHDRAWN, VALIDATING, SURVEY_SCHEDULED,
                QUALIFIED, AGREEMENT_PENDING, ONBOARDED)) {
            assertEquals(List.of(), lead(status, ASSIGNEE).allowedNextStatuses(), status.name());
        }
    }

    @Test
    void changeStatus_closeWithoutReason_throwsOnReason() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        for (String reason : new String[]{null, "", "   ", "x".repeat(501)}) {
            InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                    () -> lead.changeStatus(REJECTED, reason, null, null, null));
            assertEquals("reason", ex.getField());
        }
        assertEquals(ASSIGNED, lead.getStatus());
        assertNull(lead.getCloseReason());
    }

    @Test
    void changeStatus_closeWithFiveHundredCharReason_succeeds() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        lead.changeStatus(REJECTED, "x".repeat(500), null, null, null);

        assertEquals(REJECTED, lead.getStatus());
        assertEquals(500, lead.getCloseReason().length());
    }

    @Test
    void changeStatus_duplicateWithoutOf_throwsOnDuplicateOfLeadId() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> lead.changeStatus(DUPLICATE, "same owner", null, null, null));

        assertEquals("duplicateOfLeadId", ex.getField());
        assertEquals(ASSIGNED, lead.getStatus());
    }

    @Test
    void changeStatus_duplicateOfItself_throwsOnDuplicateOfLeadId() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> lead.changeStatus(DUPLICATE, "same owner", lead.getId(), null, null));

        assertEquals("duplicateOfLeadId", ex.getField());
        assertEquals(ASSIGNED, lead.getStatus());
    }

    @Test
    void changeStatus_duplicateOfAnotherLead_storesIt() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);
        OwnerLeadId other = OwnerLeadId.generate();

        lead.changeStatus(DUPLICATE, "same owner", other, null, null);

        assertEquals(DUPLICATE, lead.getStatus());
        assertEquals(other, lead.getDuplicateOfLeadId());
    }

    @Test
    void changeStatus_rejectedWithDuplicateOf_doesNotStoreIt() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        lead.changeStatus(REJECTED, "not a fit", OwnerLeadId.generate(), null, null);

        assertNull(lead.getDuplicateOfLeadId());
    }

    @Test
    void changeStatus_closing_clearsNextActionAtAndStoresReason() {
        OwnerLead lead = lead(CONTACTED, ASSIGNEE, NEXT_ACTION, 3L);

        lead.changeStatus(WITHDRAWN, "  owner changed  \n mind ", null, null,
                Instant.parse("2026-12-01T09:00:00Z"));

        assertEquals(WITHDRAWN, lead.getStatus());
        assertEquals("owner changed mind", lead.getCloseReason());
        assertNull(lead.getNextActionAt());
    }

    @Test
    void changeStatus_toContacted_keepsNextActionAtWhenNoneGiven() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE, NEXT_ACTION, 3L);

        lead.changeStatus(CONTACTED, null, null, null, null);

        assertEquals(CONTACTED, lead.getStatus());
        assertEquals(NEXT_ACTION, lead.getNextActionAt());
    }

    @Test
    void changeStatus_withNextActionAt_setsIt() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE, NEXT_ACTION, 3L);
        Instant later = Instant.parse("2026-12-01T09:00:00Z");

        LeadChange change = lead.changeStatus(ON_HOLD, null, null, "waiting for the owner", later);

        assertEquals(later, lead.getNextActionAt());
        assertEquals(later, change.nextActionAt());
        assertEquals("waiting for the owner", change.note());
    }

    @Test
    void changeStatus_returnsStatusChangedWithFromAndTo() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        LeadChange change = lead.changeStatus(CONTACTED, null, null, null, null);

        assertEquals(OwnerLeadActivityType.STATUS_CHANGED, change.type());
        assertEquals(ASSIGNED, change.fromStatus());
        assertEquals(CONTACTED, change.toStatus());
        assertNull(change.fromAssigneeId());
        assertNull(change.toAssigneeId());
        assertNull(change.note());
    }

    @Test
    void changeStatus_realChange_movesUpdatedAtAndLeavesVersion() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        lead.changeStatus(CONTACTED, null, null, null, null);

        assertTrue(lead.getUpdatedAt().isAfter(LONG_AGO));
        assertEquals(3L, lead.getVersion());
    }

    @Test
    void changeStatus_failedChange_leavesUpdatedAtAlone() {
        OwnerLead lead = lead(NEW, null);

        assertThrows(InvalidLeadTransitionException.class, () -> lead.changeStatus(CONTACTED, null, null, null, null));

        assertEquals(LONG_AGO, lead.getUpdatedAt());
    }

    @Test
    void changeStatus_noteGiven_isStoredTrimmedInTheChange() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        LeadChange change = lead.changeStatus(CONTACTED, null, null, "  called, no answer  ", null);

        assertEquals("called, no answer", change.note());
    }

    @Test
    void changeStatus_multiLineNote_keepsInnerNewlinesAndSpacesInNfc() {
        LeadChange change = lead(ASSIGNED, ASSIGNEE).changeStatus(CONTACTED, null, null,
                "  Line one\n  cafe\u0301   two\n\nend  ", null);

        assertEquals("Line one\n  caf\u00e9   two\n\nend", change.note());
    }

    @Test
    void changeStatus_blankOrOver2000Note_throwsOnNote() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        for (String note : new String[]{"", "   ", "x".repeat(2001)}) {
            InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                    () -> lead.changeStatus(CONTACTED, null, null, note, null));
            assertEquals("note", ex.getField());
        }
        assertEquals(ASSIGNED, lead.getStatus());
    }

    @Test
    void changeStatus_nullTarget_throwsOnToStatus() {
        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> lead(ASSIGNED, ASSIGNEE).changeStatus(null, null, null, null, null));

        assertEquals("toStatus", ex.getField());
    }

    @Test
    void changeStatus_closedLead_throwsWithEmptyAllowed() {
        for (OwnerLeadStatus closed : CLOSING) {
            OwnerLead lead = lead(closed, ASSIGNEE);

            InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                    () -> lead.changeStatus(CONTACTED, null, null, null, null));

            assertEquals(closed, ex.getFrom());
            assertEquals(CONTACTED, ex.getTo());
            assertEquals(List.of(), ex.getAllowed());
        }
    }

    @Test
    void changeStatus_invalidReason_messageNeverContainsTheSubmittedValue() {
        OwnerLead lead = lead(ASSIGNED, ASSIGNEE);

        InvalidLeadCommandException ex = assertThrows(InvalidLeadCommandException.class,
                () -> lead.changeStatus(REJECTED, "secret-reason-" + "x".repeat(600), null, null, null));

        assertFalse(ex.getMessage().contains("secret-reason"));
    }
}
