package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistSnapshot;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.SALE_A;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.SALE_B;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.leadPlace;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.visited;

/**
 * Who may edit a draft and why not (survey-checklist spec 4.4; W16-14), whether a checklist can be started on the
 * current visit (spec 7.2 {@code checklistStart}) and the two address differences of G1 (spec 4.6, W15 A-16).
 */
class ChecklistAccessPoliciesTest {

    private static final String CHECKLIST = "01JCHECK000000000000000001";
    private static final String VISIT = "01JVISIT000000000000000001";
    private static final String NEWER = "01JVISIT000000000000000002";

    // ==================== editability (spec 4.4) ====================

    @Test
    void editability_assigneeHolderOnTheCurrentVisit_isEditable() {
        assertEquals(new Editability(true, null), edit(SALE_A, 1, "SURVEY_SCHEDULED", SALE_A, true, VISIT, SALE_A));
    }

    @Test
    void editability_reasonsInTheOrderOfSpec44() {
        // a requirement change wins over every other reason, also after changing back (a new cycle)
        assertEquals(ReadOnlyReason.INTENT_CHANGED, edit(SALE_A, 3, "ON_HOLD", SALE_B, false, NEWER, SALE_B).reason());
        assertEquals(ReadOnlyReason.NEWER_VISIT, edit(SALE_A, 1, "ON_HOLD", SALE_B, false, NEWER, SALE_B).reason());
        assertEquals(ReadOnlyReason.LEAD_STATUS, edit(SALE_A, 1, "CONTACTED", SALE_B, false, VISIT, SALE_B).reason());
        assertEquals(ReadOnlyReason.NOT_ASSIGNEE, edit(SALE_A, 1, "SURVEY_SCHEDULED", SALE_B, false, VISIT, SALE_A)
                .reason());
        assertEquals(ReadOnlyReason.ASSIGNEE_INELIGIBLE, edit(SALE_A, 1, "SURVEY_SCHEDULED", SALE_B, false, VISIT,
                SALE_B).reason());
        assertEquals(ReadOnlyReason.TAKEOVER_REQUIRED, edit(SALE_A, 1, "SURVEY_SCHEDULED", SALE_B, true, VISIT,
                SALE_B).reason());
    }

    /** W16-14: a SCHEDULED re-survey keeps the VISITED appointment the current visit, so editing goes on. */
    @Test
    void editability_scheduledResurvey_keepsEditing() {
        assertEquals(new Editability(true, null), edit(SALE_A, 1, "SURVEY_SCHEDULED", SALE_A, true, VISIT, SALE_A));
    }

    @Test
    void editability_noCurrentVisitInTheCycle_isNewerVisit() {
        SurveyChecklistSnapshot checklist = new SurveyChecklistSnapshot(CHECKLIST, VISIT, 1, SALE_A,
                ChecklistStatus.DRAFT);

        assertEquals(ReadOnlyReason.NEWER_VISIT, ChecklistEditability.of(checklist, "SURVEY_SCHEDULED", 1, SALE_A,
                true, Optional.empty(), SALE_A).reason());
    }

    // ==================== start (spec 7.2 checklistStart) ====================

    @Test
    void start_performerAsAssignee_isAllowedWithoutIntake() {
        SurveyAppointment visit = visited(SurveyIntent.LONG_TERM_RENT);

        assertEquals(new ChecklistStart(true, visit.getId().getValue(), false, null), ChecklistStartPolicy.of(
                Optional.of(visit), false, "SURVEY_SCHEDULED", SALE_A, true, SALE_A));
    }

    @Test
    void start_assigneeWhoIsNotThePerformer_needsTheIntakeDeclaration() {
        SurveyAppointment visit = visited(SurveyIntent.LONG_TERM_RENT);

        assertEquals(new ChecklistStart(true, visit.getId().getValue(), true, null), ChecklistStartPolicy.of(
                Optional.of(visit), false, "SURVEY_SCHEDULED", SALE_B, true, SALE_B));
    }

    @Test
    void start_blockedByInTheOrderOfSpec72() {
        SurveyAppointment longTerm = visited(SurveyIntent.LONG_TERM_RENT);
        SurveyAppointment shortTerm = visited(SurveyIntent.SHORT_TERM_RENT);

        assertEquals(StartBlock.NO_CURRENT_VISIT, block(Optional.empty(), true, "ON_HOLD", SALE_B, false, SALE_A));
        assertEquals(StartBlock.INTENT, block(Optional.of(shortTerm), true, "ON_HOLD", SALE_B, false, SALE_A));
        assertEquals(StartBlock.LEAD_STATUS, block(Optional.of(longTerm), true, "ON_HOLD", SALE_B, false, SALE_A));
        assertEquals(StartBlock.ALREADY_STARTED, block(Optional.of(longTerm), true, "SURVEY_SCHEDULED", SALE_B, false,
                SALE_A));
        assertEquals(StartBlock.NOT_ASSIGNEE, block(Optional.of(longTerm), false, "SURVEY_SCHEDULED", SALE_B, false,
                SALE_A));
        assertEquals(StartBlock.ASSIGNEE_INELIGIBLE, block(Optional.of(longTerm), false, "SURVEY_SCHEDULED", SALE_A,
                false, SALE_A));
        ChecklistStart blocked = ChecklistStartPolicy.of(Optional.of(longTerm), true, "SURVEY_SCHEDULED", SALE_A,
                true, SALE_A);
        assertEquals(new ChecklistStart(false, null, false, StartBlock.ALREADY_STARTED), blocked);
    }

    // ==================== location difference (spec 4.6, W15 A-16) ====================

    @Test
    void difference_fromLeadAndFromMeetingPlace_areBothComputed() {
        MeetingLocation elsewhere = MeetingLocation.of("K12/5 Le Duan", "48", "Da Nang", "20195", "Thanh Khe", null);
        ObservedLocation sameAsLead = new ObservedLocation("  12 bach  dang", "48", "20194",
                new BigDecimal("16.0544071"), new BigDecimal("108.202167"));
        ObservedLocation otherWard = new ObservedLocation("12 Bach Dang", "48", "20195", null, null);

        assertEquals(new LocationDifference(false, true), LocationDifference.of(sameAsLead, leadPlace(), elsewhere));
        assertEquals(new LocationDifference(true, true), LocationDifference.of(otherWard, leadPlace(), elsewhere));
        assertEquals(new LocationDifference(false, false), LocationDifference.of(sameAsLead, leadPlace(),
                MeetingLocation.fromLead(leadPlace())));
        assertEquals(LocationDifference.NONE, LocationDifference.of(null, leadPlace(), elsewhere));
    }

    @Test
    void placeComparison_keepsTheMeetingPlaceAnswers() {
        MeetingLocation moved = MeetingLocation.of("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau",
                new Coordinates(new BigDecimal("16.06"), new BigDecimal("108.2")));

        assertEquals(true, moved.differsFrom(leadPlace()));
        assertEquals(false, MeetingLocation.fromLead(leadPlace()).differsFrom(leadPlace()));
    }

    // ==================== helpers ====================

    private static Editability edit(String holder, int leadCycle, String leadStatus, String assignee,
                                    boolean eligible, String currentVisit, String viewer) {
        return ChecklistEditability.of(new SurveyChecklistSnapshot(CHECKLIST, VISIT, 1, holder, ChecklistStatus.DRAFT),
                leadStatus, leadCycle, assignee, eligible, Optional.of(currentVisit), viewer);
    }

    private static StartBlock block(Optional<SurveyAppointment> visit, boolean hasDraft, String leadStatus,
                                    String assignee, boolean eligible, String viewer) {
        return ChecklistStartPolicy.of(visit, hasDraft, leadStatus, assignee, eligible, viewer).blockedByOrNull();
    }
}
