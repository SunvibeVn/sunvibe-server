package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DRAFT → SUBMITTED → PASSED | NEEDS_CHANGES | FAILED; nothing returns to DRAFT (survey-checklist submission 4.1). */
class ChecklistLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-11-04T02:00:00Z");
    private static final String SALE = "01JSA1E0000000000000000001";

    private static SurveyChecklist draft() {
        return SurveyChecklist.start(SurveyChecklistId.of("01JCHECK000000000000000001"), "01JLEAD0000000000000000001",
                1, SurveyAppointmentId.of("01JV1S1T000000000000000001"), 1, SALE, T0);
    }

    private static SurveyChecklist passed() {
        SurveyChecklist checklist = draft();
        checklist.submit(SALE, T0.plusSeconds(60));
        checklist.decide(DecisionOutcome.PASSED, T0.plusSeconds(120));
        return checklist;
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

    /** property-create requalification spec I-2, A-3: the mark records a fact about use; the revision stays PASSED. */
    @Test
    void lifecycle_markPassed_ok() {
        SurveyChecklist checklist = passed();

        checklist.markRequalified(T0.plusSeconds(180));

        assertTrue(checklist.isRequalified());
        assertEquals(T0.plusSeconds(180), checklist.getRequalifiedAt());
        assertEquals(ChecklistStatus.PASSED, checklist.getStatus());
        assertEquals(T0.plusSeconds(120), checklist.getDecidedAt());
    }

    @Test
    void lifecycle_passed_isNotRequalified() {
        SurveyChecklist checklist = passed();

        assertFalse(checklist.isRequalified());
        assertNull(checklist.getRequalifiedAt());
    }

    @Test
    void lifecycle_markNonPassed_refused() {
        SurveyChecklist submitted = draft();
        submitted.submit(SALE, T0.plusSeconds(60));
        SurveyChecklist returned = draft();
        returned.submit(SALE, T0.plusSeconds(60));
        returned.decide(DecisionOutcome.NEEDS_CHANGES, T0.plusSeconds(120));
        SurveyChecklist failed = draft();
        failed.submit(SALE, T0.plusSeconds(60));
        failed.decide(DecisionOutcome.FAILED, T0.plusSeconds(120));

        for (SurveyChecklist checklist : List.of(draft(), submitted, returned, failed)) {
            assertThrows(IllegalStateException.class, () -> checklist.markRequalified(T0.plusSeconds(180)),
                    checklist.getStatus().name());
            assertFalse(checklist.isRequalified(), checklist.getStatus().name());
        }
    }

    /** spec I-2: a mark is never removed or moved. */
    @Test
    void lifecycle_markTwice_refused() {
        SurveyChecklist checklist = passed();
        checklist.markRequalified(T0.plusSeconds(180));

        assertThrows(IllegalStateException.class, () -> checklist.markRequalified(T0.plusSeconds(240)));

        assertEquals(T0.plusSeconds(180), checklist.getRequalifiedAt());
    }
}
