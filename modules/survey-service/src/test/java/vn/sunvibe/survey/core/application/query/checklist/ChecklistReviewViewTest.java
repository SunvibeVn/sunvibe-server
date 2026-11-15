package vn.sunvibe.survey.core.application.query.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * survey-checklist SV-310 pin: the reviewer's texts are private (survey BR-4, BR-14), so the review part of a read
 * never prints the decision's texts or a mark's reason.
 */
class ChecklistReviewViewTest {

    private static final String REASON = "The kitchen is out of focus";

    private final ChecklistDecision decision = new ChecklistDecision(SurveyChecklistId.generate(),
            DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, "Retake the kitchen", "Owner was away",
            "Roof not reached", "U1", Instant.parse("2026-11-15T03:00:00Z"), "SURVEY_SCHEDULED", "SURVEY_SCHEDULED",
            List.of(new EvidenceRejection("M1", REASON)));

    @Test
    void toString_hidesTheTexts() {
        String text = new ChecklistReviewView(null, decision, new ReviewAccess(false, null, List.of()), null, null)
                .toString();

        assertEquals("ChecklistReviewView[hidden]", text);
        for (String hidden : List.of("Retake the kitchen", "Owner was away", "Roof not reached", REASON)) {
            assertFalse(text.contains(hidden), hidden);
        }
    }
}
