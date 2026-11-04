package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.query.ChecklistReviewView;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewReason;
import vn.sunvibe.survey.core.domain.policy.checklist.UsabilityReason;
import vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEAD;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.LEADER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.MANAGER;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.SALE_A;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.all;
import static vn.sunvibe.survey.core.testsupport.ChecklistServiceHarness.own;

/**
 * What a checklist read says about its review (survey-checklist submission spec 4.2, 4.7, 7.2): who can decide and why
 * not, in the order of the command, the PASSED rules that fail now and whether a PASSED revision can still be used.
 */
class ChecklistReviewReadTest {

    private final ChecklistServiceHarness h = new ChecklistServiceHarness();

    private ChecklistReviewView read(SurveyViewer viewer, SurveyChecklist checklist) {
        return h.queries.read(viewer, checklist.getId().getValue()).review();
    }

    private ReviewReason reason(SurveyViewer viewer, SurveyChecklist checklist) {
        return read(viewer, checklist).review().reasonOrNull();
    }

    private void freeze(SurveyChecklist checklist) {
        h.evidence.saveAll(checklist.getId(), h.media.all().stream()
                .filter(photo -> photo.getChecklistId().equals(checklist.getId().getValue()))
                .map(ChecklistReviewReadTest::entry).toList());
    }

    private static EvidenceEntry entry(SurveyMedia photo) {
        return new EvidenceEntry(photo.getId().getValue(), photo.getCategory(), photo.getPlacement().spaceIdOrNull(),
                photo.getPlacement().spaceCodeAtUploadOrNull(), "a".repeat(64));
    }

    @Test
    void read_reviewReasonsInCommandOrder() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);

        assertEquals(ReviewReason.NOT_REVIEWER_ROLE, reason(own(SALE_A), checklist));
        assertEquals(ReviewReason.NOT_SUBMITTED, reason(all(MANAGER), checklist));

        h.submitted(checklist.getId(), SALE_A);
        h.switches.serving = false;
        h.leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 2, MANAGER, 4L);
        assertEquals(ReviewReason.LEAD_STATUS, reason(all(MANAGER), checklist));

        h.leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 2, MANAGER, 4L);
        assertEquals(ReviewReason.INTENT_CHANGED, reason(all(MANAGER), checklist));

        h.leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, MANAGER, 4L);
        assertEquals(ReviewReason.SERVING_DISABLED, reason(all(MANAGER), checklist));

        h.switches.serving = true;
        assertEquals(ReviewReason.NOT_INDEPENDENT, reason(all(MANAGER), checklist));

        h.lead(LEAD, "SURVEY_SCHEDULED", SALE_A);
        ReviewAccess free = read(all(MANAGER), checklist).review();
        assertTrue(free.canReview());
        assertNull(free.reasonOrNull());
    }

    /** plan PD-6: a locked reviewer account answers as a caller without the role. */
    @Test
    void read_reviewerWhoCannotSignIn_notReviewerRole() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        h.submitted(checklist.getId(), SALE_A);
        h.directory.lock(MANAGER);

        assertEquals(ReviewReason.NOT_REVIEWER_ROLE, reason(all(MANAGER), checklist));
    }

    @Test
    void read_draft_hasNoSubmissionDecisionOrBlockers() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);

        ChecklistReviewView review = read(all(MANAGER), checklist);

        assertNull(review.submissionOrNull());
        assertNull(review.decisionOrNull());
        assertNull(review.passedOrNull());
        assertEquals(List.of(), review.review().decisionBlockedBy());
    }

    @Test
    void read_decisionBlockedBy_listsThePassedRulesThatFailNow() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        freeze(checklist);
        h.submitted(checklist.getId(), SALE_A);
        assertEquals(List.of(), read(all(MANAGER), checklist).review().decisionBlockedBy());

        SurveyMedia held = h.media.all().get(0);
        held.hold(LEADER, HoldReason.SECURITY_INCIDENT, java.time.Instant.now());
        h.media.put(held);
        h.scheduled(LEAD, SALE_A);

        assertEquals(List.of("evidence.unavailable", "appointment", "evidence.coverage"),
                read(all(MANAGER), checklist).review().decisionBlockedBy());
    }

    /** A viewer without the role sees the same rules: the page explains the state to whoever reads it. */
    @Test
    void read_decided_noBlockers() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        h.submitted(checklist.getId(), SALE_A);
        h.decided(checklist.getId(), DecisionOutcome.FAILED, null,
                vn.sunvibe.survey.core.domain.model.checklist.FailedKind.TEMPORARY, LEADER);

        assertEquals(List.of(), read(all(MANAGER), checklist).review().decisionBlockedBy());
    }

    @Test
    void read_submission_evidenceNotJudgedBeforeTheDecision() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        freeze(checklist);
        h.submitted(checklist.getId(), SALE_A);

        ChecklistReviewView review = read(own(SALE_A), checklist);

        assertEquals(SALE_A, review.submissionOrNull().submission().submittedByUserId());
        assertEquals(4, review.submissionOrNull().evidence().size());
        assertNull(review.submissionOrNull().evidence().get(0).acceptedOrNull());
    }

    @Test
    void read_passed_usableWithTheSurveyedScope() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        freeze(checklist);
        h.submitted(checklist.getId(), SALE_A);
        h.decided(checklist.getId(), DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "QUALIFIED", SALE_A);

        ChecklistReviewView review = read(own(SALE_A), checklist);

        assertTrue(review.passedOrNull().usable());
        assertEquals(RentalScope.WHOLE, review.passedOrNull().rentalScope());
        assertEquals(1, review.passedOrNull().spaces().size());
        assertTrue(review.submissionOrNull().evidence().stream().allMatch(entry -> entry.acceptedOrNull()),
                "a decision without marks accepts every photo (spec A-5)");
    }

    @Test
    void read_passed_heldPhoto_notUsable() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        freeze(checklist);
        h.submitted(checklist.getId(), SALE_A);
        h.decided(checklist.getId(), DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "QUALIFIED", SALE_A);
        SurveyMedia held = h.media.all().get(0);
        held.hold(LEADER, HoldReason.SECURITY_INCIDENT, java.time.Instant.now());
        h.media.put(held);

        ChecklistReviewView review = read(own(SALE_A), checklist);

        assertFalse(review.passedOrNull().usable());
        assertEquals(UsabilityReason.EVIDENCE_HELD, review.passedOrNull().reasonOrNull());
    }

    @Test
    void read_passed_closedLead_notUsable() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        freeze(checklist);
        h.submitted(checklist.getId(), SALE_A);
        h.decided(checklist.getId(), DecisionOutcome.PASSED, null, null, LEADER);
        h.lead(LEAD, "WITHDRAWN", SALE_A);

        assertEquals(UsabilityReason.LEAD_CLOSED, read(own(SALE_A), checklist).passedOrNull().reasonOrNull());
    }

    @Test
    void read_evidenceCategoryKept() {
        SurveyChecklist checklist = h.ready(LEAD, SALE_A);
        freeze(checklist);
        h.submitted(checklist.getId(), SALE_A);

        assertTrue(read(own(SALE_A), checklist).submissionOrNull().evidence().stream()
                .anyMatch(entry -> entry.entry().category() == EvidenceCategory.KITCHEN));
    }
}
