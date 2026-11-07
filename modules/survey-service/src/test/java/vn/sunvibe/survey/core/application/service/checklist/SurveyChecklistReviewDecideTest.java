package vn.sunvibe.survey.core.application.service.checklist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.command.checklist.DecideChecklistCommand;
import vn.sunvibe.survey.core.application.command.checklist.SubmitChecklistCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistDecisionNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReviewNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistDecideResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.MANAGER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_B;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;

/**
 * The check order of a decision (survey-checklist submission spec 5.5) and what it writes (spec 4.5, 5.4): each refusal
 * comes before the next one, which a revision breaking both rules shows.
 */
class SurveyChecklistReviewDecideTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();
    private SurveyChecklist checklist;
    private long version;

    @BeforeEach
    void submitted() {
        checklist = h.ready(LEAD, SALE_A);
        version = h.reviews.submit(own(SALE_A), new SubmitChecklistCommand(checklist.getId().getValue(), 0L)).view()
                .checklist().getVersion();
    }

    private DecideChecklistCommand passed(Long expected) {
        return new DecideChecklistCommand(checklist.getId().getValue(), expected, DecisionOutcome.PASSED, null, null, null,
                null, null, List.of());
    }

    private DecideChecklistCommand returned(NeedsChangesKind kind) {
        return new DecideChecklistCommand(checklist.getId().getValue(), version, DecisionOutcome.NEEDS_CHANGES, kind,
                null, "Thiếu giấy tờ gốc", null, null, List.of());
    }

    private DecideChecklistCommand failed(FailedKind kind) {
        return new DecideChecklistCommand(checklist.getId().getValue(), version, DecisionOutcome.FAILED, null, kind,
                "Không phù hợp", null, null, List.of());
    }

    // ==================== check order ====================

    @Test
    void decide_body_beforeAnyLookup() {
        h.journal.clear();

        assertThrows(ChecklistValidationException.class, () -> h.reviews.decide(all(LEADER),
                new DecideChecklistCommand("not-an-id", 0L, null, null, null, null, null, null, List.of())));

        assertEquals(List.of(), h.journal);
    }

    @Test
    void decide_textRules_bodyViolationsOnTheirPaths() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> h.reviews.decide(all(LEADER), new DecideChecklistCommand(checklist.getId().getValue(), version,
                        DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, "x".repeat(2001), null,
                        null, List.of())));

        assertEquals("reason", refused.violations().get(0).path());
    }

    @Test
    void decide_phoneInTheReason_refused() {
        assertThrows(ChecklistValidationException.class, () -> h.reviews.decide(all(LEADER),
                new DecideChecklistCommand(checklist.getId().getValue(), version, DecisionOutcome.NEEDS_CHANGES,
                        NeedsChangesKind.DOCUMENTS_ONLY, null, "Gọi 0912345678", null, null, List.of())));
    }

    /** plan PD-6: a locked account is refused like a role, before anything is looked up. */
    @Test
    void decide_cannotSignIn_403BeforeTheLookup() {
        h.directory.lock(LEADER);
        h.journal.clear();

        assertThrows(ChecklistReviewNotAllowedException.class, () -> h.reviews.decide(all(LEADER), passed(version)));

        assertEquals(List.of(), h.journal);
    }

    @Test
    void decide_hiddenLead_404WithEvent() {
        assertThrows(ChecklistNotFoundException.class, () -> h.reviews.decide(own(SALE_B), passed(version)));

        assertEquals(SecurityAction.REVIEW, h.securityEvents.all().get(0).action());
        assertEquals(SecurityOutcome.NOT_VISIBLE, h.securityEvents.all().get(0).outcome());
    }

    @Test
    void decide_staleVersion_beforeTheState() {
        h.leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 5L);

        assertThrows(SurveyConcurrentModificationException.class, () -> h.reviews.decide(all(LEADER),
                passed(version + 1)));
    }

    @Test
    void decide_state_beforeTheExclusion() {
        h.leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 5L);

        ChecklistDecisionNotAllowedException refused = assertThrows(ChecklistDecisionNotAllowedException.class,
                () -> h.reviews.decide(all(SALE_A), passed(version)));

        assertEquals(List.of("lead.status"), refused.fields());
        assertEquals(0, h.securityEvents.all().size());
    }

    @Test
    void decide_excluded_403WithEvent() {
        assertThrows(ChecklistReviewNotAllowedException.class, () -> h.reviews.decide(all(SALE_A), passed(version)));

        assertEquals(SecurityOutcome.NOT_INDEPENDENT, h.securityEvents.all().get(0).outcome());
        assertEquals(SecurityAction.REVIEW, h.securityEvents.all().get(0).action());
        assertEquals(ChecklistStatus.SUBMITTED, h.checklists.findById(checklist.getId()).orElseThrow().getStatus());
    }

    @Test
    void decide_servingOff_409() {
        h.switches.serving = false;

        ChecklistDecisionNotAllowedException refused = assertThrows(ChecklistDecisionNotAllowedException.class,
                () -> h.reviews.decide(all(LEADER), passed(version)));

        assertEquals(List.of("media.serving"), refused.fields());
    }

    @Test
    void decide_secondTime_statusRefused() {
        ChecklistDecideResult first = h.reviews.decide(all(LEADER), returned(NeedsChangesKind.DOCUMENTS_ONLY));

        ChecklistDecisionNotAllowedException refused = assertThrows(ChecklistDecisionNotAllowedException.class,
                () -> h.reviews.decide(all(MANAGER), passed(first.view().checklist().getVersion())));

        assertEquals(List.of("status"), refused.fields());
    }

    @Test
    void decide_markOutsideTheFrozenSet_400() {
        ChecklistValidationException refused = assertThrows(ChecklistValidationException.class,
                () -> h.reviews.decide(all(LEADER), new DecideChecklistCommand(checklist.getId().getValue(), version,
                        DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, "Lý do", null, null,
                        List.of(new DecideChecklistCommand.RejectedEvidence("01JMED1A000000000000000999", "Mờ")))));

        assertEquals("rejectedEvidence.0.mediaId", refused.violations().get(0).path());
    }

    /** R5 on the command: S3 and the booking guard prevent it, it is checked anyway. */
    @Test
    void decide_scheduledAppointment_refusesPassedOnly() {
        h.scheduled(LEAD, SALE_A);

        ChecklistDecisionNotAllowedException refused = assertThrows(ChecklistDecisionNotAllowedException.class,
                () -> h.reviews.decide(all(LEADER), passed(version)));

        assertEquals(List.of("appointment"), refused.fields());
    }

    // ==================== what it writes ====================

    private LeadMove onlyMove() {
        return h.leads.moves().get(h.leads.moves().size() - 1);
    }

    @Test
    void decide_passed_movesToQualified() {
        ChecklistDecideResult result = h.reviews.decide(all(LEADER), passed(version));

        assertEquals(ChecklistStatus.PASSED, result.view().checklist().getStatus());
        assertEquals("QUALIFIED", result.lead().status());
        assertEquals("PASSED", onlyMove().detailOrNull());
        assertNull(onlyMove().closeReasonOrNull());
        ChecklistDecision stored = h.decisions.findByChecklistId(checklist.getId()).orElseThrow();
        assertEquals("SURVEY_SCHEDULED", stored.leadStatusBefore());
        assertEquals("QUALIFIED", stored.leadStatusAfter());
        assertEquals(LEADER, stored.reviewerUserId());
    }

    @Test
    void decide_documentsOnly_keepsTheLead() {
        ChecklistDecideResult result = h.reviews.decide(all(LEADER), returned(NeedsChangesKind.DOCUMENTS_ONLY));

        assertEquals("SURVEY_SCHEDULED", result.lead().status());
        assertEquals("NEEDS_CHANGES:DOCUMENTS_ONLY", onlyMove().detailOrNull());
        assertEquals("SURVEY_SCHEDULED", h.decisions.findByChecklistId(checklist.getId()).orElseThrow()
                .leadStatusAfter());
    }

    @Test
    void decide_resurveyRequired_toContacted() {
        ChecklistDecideResult result = h.reviews.decide(all(LEADER), returned(NeedsChangesKind.RESURVEY_REQUIRED));

        assertEquals("CONTACTED", result.lead().status());
        assertEquals("NEEDS_CHANGES:RESURVEY_REQUIRED", onlyMove().detailOrNull());
    }

    @Test
    void decide_failedTemporary_toContacted() {
        assertEquals("CONTACTED", h.reviews.decide(all(LEADER), failed(FailedKind.TEMPORARY)).lead().status());
        assertEquals("FAILED:TEMPORARY", onlyMove().detailOrNull());
    }

    @Test
    void decide_failedOutOfScope_rejectsWithTheFixedReason() {
        assertEquals("REJECTED", h.reviews.decide(all(LEADER), failed(FailedKind.OUT_OF_SCOPE)).lead().status());
        assertEquals("Survey failed: out of scope (checklist #" + checklist.getChecklistNo() + ")",
                onlyMove().closeReasonOrNull());
    }

    @Test
    void decide_failedNotAccepted_rejectsWithTheFixedReason() {
        assertEquals("REJECTED", h.reviews.decide(all(LEADER), failed(FailedKind.NOT_ACCEPTED)).lead().status());
        assertEquals("Survey failed: not accepted (checklist #" + checklist.getChecklistNo() + ")",
                onlyMove().closeReasonOrNull());
    }

    /** spec 5.4: the lead's lock first; the move on the locked row, after the decision. */
    @Test
    void decide_locksTheLeadFirst() {
        h.journal.clear();

        h.reviews.decide(all(LEADER), passed(version));

        List<String> journal = h.journal;
        assertTrue(journal.indexOf("lead:update") < journal.indexOf("checklist:forUpdate"), journal.toString());
        assertTrue(journal.indexOf("checklist:forUpdate") < journal.indexOf("decision:insert"), journal.toString());
        assertTrue(journal.indexOf("decision:insert") < journal.indexOf("lead:CHECKLIST_REVIEWED"), journal.toString());
    }
}
