package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.domain.exception.InvalidLeadTransitionException;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.CHECKLIST_REVIEWED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.REQUALIFICATION_MARKED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_BOOKED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_CANCELLED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_CHANGED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_CONFIRMED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_RESUMED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_UNCONFIRMED;
import static vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType.SURVEY_VISITED;
import static vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus.*;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.LONG_AGO;
import static vn.sunvibe.acquisition.core.testsupport.OwnerLeadFixture.lead;

/**
 * Lead moves asked by survey commands (survey-schedule spec 4.3, survey BR-12 table, survey-schedule BR-3, BR-11,
 * US-2 AC-2): only the listed rows are allowed; every other combination is refused with no allowed status.
 */
class OwnerLeadSurveyMoveTest {

    private static final String ASSIGNEE = "01JUSERAAAAAAAAAAAAAAAAAAA";
    private static final String APPOINTMENT_ID = "01JAPPTAAAAAAAAAAAAAAAAAAA";

    /** One allowed row: the event, the status it starts from and the status it asks for (null = keep). */
    private record Row(OwnerLeadActivityType type, OwnerLeadStatus from, OwnerLeadStatus target) {
    }

    private static final List<Row> ALLOWED = List.of(
            new Row(SURVEY_BOOKED, ASSIGNED, SURVEY_SCHEDULED),
            new Row(SURVEY_BOOKED, CONTACTED, SURVEY_SCHEDULED),
            new Row(SURVEY_BOOKED, SURVEY_SCHEDULED, null),
            new Row(SURVEY_CHANGED, SURVEY_SCHEDULED, null),
            new Row(SURVEY_CONFIRMED, SURVEY_SCHEDULED, null),
            new Row(SURVEY_UNCONFIRMED, SURVEY_SCHEDULED, null),
            new Row(SURVEY_VISITED, SURVEY_SCHEDULED, null),
            new Row(SURVEY_CANCELLED, SURVEY_SCHEDULED, null),
            new Row(SURVEY_CANCELLED, SURVEY_SCHEDULED, CONTACTED),
            new Row(SURVEY_RESUMED, ON_HOLD, SURVEY_SCHEDULED),
            new Row(SURVEY_RESUMED, ON_HOLD, QUALIFIED),
            new Row(CHECKLIST_REVIEWED, SURVEY_SCHEDULED, QUALIFIED),
            new Row(CHECKLIST_REVIEWED, SURVEY_SCHEDULED, null),
            new Row(CHECKLIST_REVIEWED, SURVEY_SCHEDULED, CONTACTED),
            new Row(REQUALIFICATION_MARKED, QUALIFIED, CONTACTED),
            new Row(REQUALIFICATION_MARKED, ON_HOLD, null));

    private static final String CLOSE_REASON = "Survey failed: out of scope (checklist #1)";

    /** checklist submission spec 4.5, A-11: REJECTED keeps the fixed close reason and clears the next action. */
    @Test
    void reviewed_rejected_closesWithItsReasonAndClearsTheNextAction() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE, LONG_AGO, 3L);

        LeadChange change = lead.moveForSurvey(CHECKLIST_REVIEWED, REJECTED, APPOINTMENT_ID, 2, CLOSE_REASON);

        assertEquals(REJECTED, lead.getStatus());
        assertEquals(CLOSE_REASON, lead.getCloseReason());
        assertNull(lead.getNextActionAt());
        assertEquals(SURVEY_SCHEDULED, change.fromStatus());
        assertEquals(REJECTED, change.toStatus());
    }

    @Test
    void reviewed_qualified_keepsTheNextActionAndHasNoCloseReason() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE, LONG_AGO, 3L);

        lead.moveForSurvey(CHECKLIST_REVIEWED, QUALIFIED, APPOINTMENT_ID, 2, null);

        assertEquals(QUALIFIED, lead.getStatus());
        assertEquals(LONG_AGO, lead.getNextActionAt());
        assertNull(lead.getCloseReason());
    }

    @Test
    void reviewed_rejectedWithoutReason_refused() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE);

        assertThrows(IllegalArgumentException.class,
                () -> lead.moveForSurvey(CHECKLIST_REVIEWED, REJECTED, APPOINTMENT_ID, 2, null));
    }

    @Test
    void reviewed_closeReasonOnAnotherTarget_refused() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE);

        assertThrows(IllegalArgumentException.class,
                () -> lead.moveForSurvey(CHECKLIST_REVIEWED, CONTACTED, APPOINTMENT_ID, 2, CLOSE_REASON));
    }

    /** checklist submission spec 4.5, I-4: a lead that is not SURVEY_SCHEDULED is never decided on. */
    @Test
    void reviewed_fromOtherStatus_refused() {
        for (OwnerLeadStatus from : List.of(ON_HOLD, CONTACTED, QUALIFIED, REJECTED)) {
            OwnerLead lead = lead(from, ASSIGNEE);

            assertThrows(InvalidLeadTransitionException.class,
                    () -> lead.moveForSurvey(CHECKLIST_REVIEWED, QUALIFIED, APPOINTMENT_ID, 2), from.name());
        }
    }

    @Test
    void reviewed_toAnotherTarget_refused() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE);

        assertThrows(InvalidLeadTransitionException.class,
                () -> lead.moveForSurvey(CHECKLIST_REVIEWED, ON_HOLD, APPOINTMENT_ID, 2));
    }

    /** checklist submission spec 4.8, I-4: QUALIFIED comes back only through a resume of a held lead. */
    @Test
    void resume_toQualified_onlyFromOnHold() {
        for (OwnerLeadStatus from : List.of(SURVEY_SCHEDULED, CONTACTED, ASSIGNED)) {
            OwnerLead lead = lead(from, ASSIGNEE);

            assertThrows(InvalidLeadTransitionException.class,
                    () -> lead.moveForSurvey(SURVEY_RESUMED, QUALIFIED, APPOINTMENT_ID, 2), from.name());
        }
    }

    /** property-create requalification spec 4: a QUALIFIED lead goes back to a new survey with the mark. */
    @Test
    void move_qualifiedToContacted_ok() {
        OwnerLead lead = lead(QUALIFIED, ASSIGNEE);

        LeadChange change = lead.moveForSurvey(REQUALIFICATION_MARKED, CONTACTED, APPOINTMENT_ID, 2);

        assertEquals(CONTACTED, lead.getStatus());
        assertEquals(QUALIFIED, change.fromStatus());
        assertEquals(CONTACTED, change.toStatus());
        assertEquals(APPOINTMENT_ID, change.referenceId());
    }

    /** property-create requalification D5-2: a held lead stays held; the row still changes, so the version rises. */
    @Test
    void move_onHoldKept_ok() {
        OwnerLead lead = lead(ON_HOLD, ASSIGNEE);

        LeadChange change = lead.moveForSurvey(REQUALIFICATION_MARKED, null, APPOINTMENT_ID, 2);

        assertEquals(ON_HOLD, lead.getStatus());
        assertNull(change.fromStatus());
        assertNull(change.toStatus());
        assertTrue(lead.getUpdatedAt().isAfter(LONG_AGO));
    }

    @Test
    void move_fromOther_refused() {
        for (OwnerLeadStatus from : List.of(NEW, ASSIGNED, CONTACTED, SURVEY_SCHEDULED, DUPLICATE, REJECTED, WITHDRAWN)) {
            assertRefused(REQUALIFICATION_MARKED, from, CONTACTED);
            assertRefused(REQUALIFICATION_MARKED, from, null);
        }
        assertRefused(REQUALIFICATION_MARKED, QUALIFIED, null);
        assertRefused(REQUALIFICATION_MARKED, ON_HOLD, CONTACTED);
    }

    @Test
    void moveForSurvey_everyAllowedRow_movesOrKeepsAndCarriesTheReference() {
        for (Row row : ALLOWED) {
            OwnerLead lead = lead(row.from(), ASSIGNEE);

            LeadChange change = lead.moveForSurvey(row.type(), row.target(), APPOINTMENT_ID, 2);

            String label = row.toString();
            OwnerLeadStatus expected = row.target() == null ? row.from() : row.target();
            assertEquals(expected, lead.getStatus(), label);
            assertEquals(row.type(), change.type(), label);
            assertEquals(row.target() == null ? null : row.from(), change.fromStatus(), label);
            assertEquals(row.target(), change.toStatus(), label);
            assertEquals(APPOINTMENT_ID, change.referenceId(), label);
            assertEquals(2, change.referenceNo(), label);
            assertNull(change.note(), label);
            assertNull(change.fromAssigneeId(), label);
            assertNull(change.toAssigneeId(), label);
            assertNull(change.nextActionAt(), label);
            // survey-schedule spec A-2: every survey command bumps the lead version, so the row must be dirty
            assertTrue(lead.getUpdatedAt().isAfter(LONG_AGO), label);
            assertEquals(3L, lead.getVersion(), label);
        }
    }

    @Test
    void moveForSurvey_everyOtherCombination_isRefusedAndChangesNothing() {
        List<OwnerLeadActivityType> types = Arrays.asList(OwnerLeadActivityType.values());
        List<OwnerLeadStatus> targets = new ArrayList<>(Arrays.asList(OwnerLeadStatus.values()));
        targets.add(null);
        for (OwnerLeadActivityType type : types) {
            for (OwnerLeadStatus from : OwnerLeadStatus.values()) {
                for (OwnerLeadStatus target : targets) {
                    // the closing row needs its close reason: reviewed_rejected_closesWithItsReason...
                    if (ALLOWED.contains(new Row(type, from, target))
                            || type == CHECKLIST_REVIEWED && from == SURVEY_SCHEDULED && target == REJECTED) {
                        continue;
                    }
                    OwnerLead lead = lead(from, ASSIGNEE);
                    String label = type + " " + from + " -> " + target;

                    InvalidLeadTransitionException ex = assertThrows(InvalidLeadTransitionException.class,
                            () -> lead.moveForSurvey(type, target, APPOINTMENT_ID, 1), label);

                    assertEquals(from, ex.getFrom(), label);
                    assertTrue(Objects.equals(target, ex.getTo()), label);
                    assertEquals(List.of(), ex.getAllowed(), label);
                    assertEquals(from, lead.getStatus(), label);
                    assertEquals(LONG_AGO, lead.getUpdatedAt(), label);
                }
            }
        }
    }

    @Test
    void moveForSurvey_namedRefusals_ofThePlan() {
        assertRefused(SURVEY_BOOKED, ON_HOLD, SURVEY_SCHEDULED);
        assertRefused(SURVEY_BOOKED, NEW, SURVEY_SCHEDULED);
        assertRefused(SURVEY_BOOKED, REJECTED, SURVEY_SCHEDULED);
        assertRefused(SURVEY_BOOKED, SURVEY_SCHEDULED, SURVEY_SCHEDULED);
        assertRefused(SURVEY_CANCELLED, CONTACTED, CONTACTED);
        assertRefused(SURVEY_RESUMED, CONTACTED, SURVEY_SCHEDULED);
    }

    @Test
    void moveForSurvey_withoutReference_isABug() {
        OwnerLead lead = lead(SURVEY_SCHEDULED, ASSIGNEE);

        assertThrows(IllegalArgumentException.class, () -> lead.moveForSurvey(SURVEY_CHANGED, null, null, null));
        assertEquals(LONG_AGO, lead.getUpdatedAt());
    }

    private static void assertRefused(OwnerLeadActivityType type, OwnerLeadStatus from, OwnerLeadStatus target) {
        OwnerLead lead = lead(from, ASSIGNEE);
        assertThrows(InvalidLeadTransitionException.class,
                () -> lead.moveForSurvey(type, target, APPOINTMENT_ID, 1), type + " " + from);
        assertEquals(from, lead.getStatus());
    }
}
