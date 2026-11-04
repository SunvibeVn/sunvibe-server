package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DRAFT → SUBMITTED → PASSED | NEEDS_CHANGES | FAILED; nothing returns to DRAFT (survey-checklist submission 4.1). */
class ChecklistLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-11-04T02:00:00Z");
    private static final String SALE = "01JSA1E0000000000000000001";

    private static SurveyChecklist draft() {
        return SurveyChecklist.start(SurveyChecklistId.of("01JCHECK000000000000000001"), "01JLEAD0000000000000000001",
                1, SurveyAppointmentId.of("01JV1S1T000000000000000001"), 1, SALE, T0);
    }

    @Test
    void lifecycle_submit_keepsWhoAndWhen() {
        SurveyChecklist checklist = draft();

        checklist.submit(SALE, T0.plusSeconds(60));

        assertEquals(ChecklistStatus.SUBMITTED, checklist.getStatus());
        assertEquals(SALE, checklist.getSubmittedByUserId());
        assertEquals(T0.plusSeconds(60), checklist.getSubmittedAt());
        assertNull(checklist.getDecidedAt());
    }

    @Test
    void lifecycle_submitNonDraft_refused() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0);

        assertThrows(IllegalStateException.class, () -> checklist.submit(SALE, T0));
    }

    @Test
    void lifecycle_decidePassed_setsThePassedStatus() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0);

        checklist.decide(DecisionOutcome.PASSED, T0.plusSeconds(60));

        assertEquals(ChecklistStatus.PASSED, checklist.getStatus());
        assertEquals(T0.plusSeconds(60), checklist.getDecidedAt());
    }

    @Test
    void lifecycle_decideNeedsChanges_setsTheNeedsChangesStatus() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0);

        checklist.decide(DecisionOutcome.NEEDS_CHANGES, T0);

        assertEquals(ChecklistStatus.NEEDS_CHANGES, checklist.getStatus());
    }

    @Test
    void lifecycle_decideFailed_setsTheFailedStatus() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0);

        checklist.decide(DecisionOutcome.FAILED, T0);

        assertEquals(ChecklistStatus.FAILED, checklist.getStatus());
    }

    @Test
    void lifecycle_decideDraft_refused() {
        SurveyChecklist checklist = draft();

        assertThrows(IllegalStateException.class, () -> checklist.decide(DecisionOutcome.PASSED, T0));
    }

    @Test
    void lifecycle_decideTwice_refused() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0);
        checklist.decide(DecisionOutcome.NEEDS_CHANGES, T0);

        assertThrows(IllegalStateException.class, () -> checklist.decide(DecisionOutcome.PASSED, T0));
    }

    @Test
    void lifecycle_takeOverAfterSubmit_refused() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0);

        assertThrows(IllegalStateException.class, () -> checklist.takeOver("01JSA1E0000000000000000002"));
    }

    @Test
    void lifecycle_statuses_knowWhichAreDecided() {
        assertEquals(false, ChecklistStatus.DRAFT.isDecided());
        assertEquals(false, ChecklistStatus.SUBMITTED.isDecided());
        assertEquals(true, ChecklistStatus.PASSED.isDecided());
        assertEquals(true, ChecklistStatus.NEEDS_CHANGES.isDecided());
        assertEquals(true, ChecklistStatus.FAILED.isDecided());
    }
}
