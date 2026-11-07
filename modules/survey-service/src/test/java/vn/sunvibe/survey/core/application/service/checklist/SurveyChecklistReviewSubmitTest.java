package vn.sunvibe.survey.core.application.service.checklist;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.sunvibe.survey.core.application.command.checklist.SubmitChecklistCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotReadyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReadOnlyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistSubmissionNotAvailableException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSubmitResult;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;

/**
 * The check order of a submit (survey-checklist submission spec 5.5): each refusal comes before the next one, which a
 * revision breaking both rules shows. The lock order of spec 5.4 is read from the journal.
 */
class SurveyChecklistReviewSubmitTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    private SubmitChecklistCommand submit(SurveyChecklist checklist, Long version) {
        return new SubmitChecklistCommand(checklist.getId().getValue(), version);
    }

    private SurveyChecklist ready() {
        return h.ready(LEAD, SALE_A);
    }

    @Test
    void submit_body_beforeAnyLookup() {
        h.journal.clear();

        assertThrows(ChecklistValidationException.class,
                () -> h.reviews.submit(own(SALE_A), new SubmitChecklistCommand("not-an-id", null)));

        assertEquals(List.of(), h.journal);
    }

    @Test
    void submit_unknownChecklist_404NoEvent() {
        assertThrows(ChecklistNotFoundException.class, () -> h.reviews.submit(own(SALE_A),
                new SubmitChecklistCommand("01JCHECK000000000000000099", 0L)));

        assertEquals(0, h.securityEvents.all().size());
    }

    @Test
    void submit_hiddenLead_404WithEventBeforeTheAssignee() {
        SurveyChecklist checklist = ready();

        assertThrows(ChecklistNotFoundException.class, () -> h.reviews.submit(own(SALE_B), submit(checklist, 0L)));

        assertEquals(1, h.securityEvents.all().size());
    }

    @Test
    void submit_notAssignee_403WithEventBeforeTheState() {
        SurveyChecklist checklist = ready();
        h.leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 5L);

        assertThrows(AccessDeniedException.class, () -> h.reviews.submit(all(LEADER), submit(checklist, 0L)));

        assertEquals(1, h.securityEvents.all().size());
    }

    @Test
    void submit_readOnly_beforeEligibility() {
        SurveyChecklist checklist = ready();
        h.leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 5L);
        h.directory.makeIneligible(SALE_A);

        assertThrows(ChecklistReadOnlyException.class, () -> h.reviews.submit(own(SALE_A), submit(checklist, 0L)));
    }

    @Test
    void submit_ineligible_beforeTheHolder() {
        SurveyChecklist checklist = ready();
        h.leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_B, 5L);
        h.directory.makeIneligible(SALE_B);

        assertThrows(SurveyorIneligibleException.class, () -> h.reviews.submit(own(SALE_B), submit(checklist, 99L)));
    }

    @Test
    void submit_notTheHolder_beforeTheVersion() {
        SurveyChecklist checklist = ready();
        h.leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_B, 5L);

        assertThrows(ChecklistTakeoverRequiredException.class,
                () -> h.reviews.submit(own(SALE_B), submit(checklist, 99L)));
    }

    @Test
    void submit_staleVersion_beforeTheSwitches() {
        SurveyChecklist checklist = ready();
        h.submissionOn = false;

        assertThrows(SurveyConcurrentModificationException.class,
                () -> h.reviews.submit(own(SALE_A), submit(checklist, 99L)));
    }

    @Test
    void submit_switchOff_beforeTheGates() {
        SurveyChecklist checklist = ready();
        h.submissionOn = false;
        h.scheduled(LEAD, SALE_A);

        assertThrows(ChecklistSubmissionNotAvailableException.class,
                () -> h.reviews.submit(own(SALE_A), submit(checklist, 0L)));
    }

    @Test
    void submit_gates_oneFieldEachInOrder() {
        SurveyChecklist checklist = ready();
        h.scheduled(LEAD, SALE_A);

        ChecklistNotReadyException refused = assertThrows(ChecklistNotReadyException.class,
                () -> h.reviews.submit(own(SALE_A), submit(checklist, 0L)));

        assertEquals(List.of("appointment"), refused.fields());
    }

    @Test
    void submit_locksInTheOrderOfSpec54() {
        SurveyChecklist checklist = ready();
        h.journal.clear();

        h.reviews.submit(own(SALE_A), submit(checklist, 0L));

        List<String> journal = h.journal;
        assertTrue(journal.indexOf("lead:share") < journal.indexOf("checklist:forUpdate"), journal.toString());
        assertTrue(journal.indexOf("checklist:forUpdate") < journal.indexOf("checklist:update"), journal.toString());
        assertTrue(journal.indexOf("checklist:update") < journal.indexOf("submission:insert"), journal.toString());
        assertTrue(journal.indexOf("submission:insert") < journal.indexOf("evidence:insert"), journal.toString());
        assertEquals("lead:activity:CHECKLIST_SUBMITTED", journal.stream().filter(line -> line.startsWith("lead:activity"))
                .findFirst().orElseThrow());
    }

    @Test
    void submit_writesTheFrozenRows() {
        SurveyChecklist checklist = ready();

        ChecklistSubmitResult result = h.reviews.submit(own(SALE_A), submit(checklist, 0L));

        assertEquals(ChecklistStatus.SUBMITTED, result.view().checklist().getStatus());
        assertNotNull(result.historyEntryOrNull());
        assertEquals(1, h.submissions.all().size());
        assertEquals(4, h.evidence.findByChecklistId(checklist.getId()).size());
    }

    @Test
    void submit_repeatedByTheCaller_replaysWithoutWriting() {
        SurveyChecklist checklist = ready();
        h.reviews.submit(own(SALE_A), submit(checklist, 0L));
        h.journal.clear();

        ChecklistSubmitResult replay = h.reviews.submit(own(SALE_A), submit(checklist, 0L));

        assertNull(replay.historyEntryOrNull());
        assertEquals(1, h.submissions.all().size());
        assertTrue(h.journal.stream().noneMatch(line -> line.contains("insert") || line.contains("activity")),
                h.journal.toString());
    }
}
