package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.usecase.LeadSurveyState;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;

/** What the lead rules ask of the survey record (survey-checklist submission spec 4.7, 4.8, plan PD-3). */
class SurveyStateServiceTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    private LeadSurveyState state() {
        return h.surveyStates.surveyState(LEAD, 1);
    }

    private SurveyChecklistId draft() {
        return h.started(LEAD, SALE_A).view().checklist().getId();
    }

    @Test
    void surveyState_noRevision_visitIsCompletable() {
        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        h.visited(LEAD, vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent.LONG_TERM_RENT, 1, SALE_A);

        assertEquals(new LeadSurveyState(false, false, false, true), state());
    }

    @Test
    void surveyState_noVisit_notCompletable() {
        h.lead(LEAD, "CONTACTED", SALE_A);

        assertEquals(new LeadSurveyState(false, false, false, false), state());
    }

    @Test
    void surveyState_draft_completable() {
        draft();

        assertEquals(new LeadSurveyState(false, false, false, true), state());
    }

    @Test
    void surveyState_submitted_hasSubmittedAndCompletable() {
        h.submitted(draft(), SALE_A);

        assertEquals(new LeadSurveyState(false, false, true, true), state());
    }

    @Test
    void surveyState_passedUsable() {
        SurveyChecklistId id = draft();
        frozenPhoto(id);
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);

        assertEquals(new LeadSurveyState(true, true, false, false), state());
    }

    /** property-create requalification spec 4: after a mark the cycle reads as before a PASSED. */
    @Test
    void state_afterMark_noPassed() {
        SurveyChecklistId id = passedRevision();

        mark(id);

        assertEquals(new LeadSurveyState(false, false, false, false), state());
    }

    /** property-create requalification D5-1: the current PASSED is the unmarked one, whatever its age. */
    @Test
    void state_secondPassed_isCurrent() {
        mark(passedRevision());
        SurveyChecklistId second = draft();
        frozenPhoto(second);
        h.submitted(second, SALE_A);
        h.decided(second, DecisionOutcome.PASSED, null, null, LEADER);

        assertEquals(new LeadSurveyState(true, true, false, false), state());
    }

    /** spec 4.7: a hold makes the PASSED unusable until its release; the revision itself stays PASSED. */
    @Test
    void surveyState_passedWithHeldPhoto_notUsable_thenReleased() {
        SurveyChecklistId id = draft();
        SurveyMedia photo = frozenPhoto(id);
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);

        photo.hold(MediaFixture.ADMIN, HoldReason.SECURITY_INCIDENT, Instant.now());
        h.media.put(photo);

        assertEquals(new LeadSurveyState(true, false, false, false), state());

        photo.release(MediaFixture.ADMIN, Instant.now());
        h.media.put(photo);

        assertEquals(new LeadSurveyState(true, true, false, false), state());
    }

    @Test
    void surveyState_resurveyMark_notCompletable() {
        SurveyChecklistId id = draft();
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.RESURVEY_REQUIRED, null, LEADER);

        assertEquals(new LeadSurveyState(false, false, false, false), state());
    }

    @Test
    void surveyState_failedTemporary_notCompletable() {
        SurveyChecklistId id = draft();
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.FAILED, null, FailedKind.TEMPORARY, LEADER);

        assertEquals(new LeadSurveyState(false, false, false, false), state());
    }

    @Test
    void surveyState_documentsOnly_completable() {
        SurveyChecklistId id = draft();
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, LEADER);

        assertEquals(new LeadSurveyState(false, false, false, true), state());
    }

    /** spec I-1: a revision of an ended cycle says nothing about the new one. */
    @Test
    void surveyState_otherCycle_seesNothing() {
        h.submitted(draft(), SALE_A);

        assertEquals(new LeadSurveyState(false, false, false, false), h.surveyStates.surveyState(LEAD, 2));
    }

    private SurveyChecklistId passedRevision() {
        SurveyChecklistId id = draft();
        h.submitted(id, SALE_A);
        h.decided(id, DecisionOutcome.PASSED, null, null, LEADER);
        return id;
    }

    private void mark(SurveyChecklistId id) {
        SurveyChecklist checklist = h.checklists.findById(id).orElseThrow();
        checklist.markRequalified(Instant.now());
        h.checklists.update(checklist);
    }

    private SurveyMedia frozenPhoto(SurveyChecklistId id) {
        SurveyMedia photo = h.readyPhoto(id, EvidenceCategory.EXTERIOR_ACCESS, MediaPlacement.asset());
        h.evidence.saveAll(id, List.of(new EvidenceEntry(photo.getId().getValue(), photo.getCategory(), null, null,
                MediaFixture.sha(1))));
        return photo;
    }
}
