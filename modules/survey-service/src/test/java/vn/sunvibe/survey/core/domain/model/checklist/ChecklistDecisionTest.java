package vn.sunvibe.survey.core.domain.model.checklist;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * survey-checklist SV-310 pin: the reviewer's texts are private (survey BR-4, BR-14), so a decision prints its outcome
 * and never its reason, notes, limitations or a mark's reason.
 */
class ChecklistDecisionTest {

    private static final String REASON = "The kitchen is out of focus";

    private final ChecklistDecision decision = new ChecklistDecision(SurveyChecklistId.generate(),
            DecisionOutcome.NEEDS_CHANGES, NeedsChangesKind.DOCUMENTS_ONLY, null, "Retake the kitchen", "Owner was away",
            "Roof not reached", "U1", Instant.parse("2026-11-15T03:00:00Z"), "SURVEY_SCHEDULED", "SURVEY_SCHEDULED",
            List.of(new EvidenceRejection("M1", REASON)));

    @Test
    void toString_hidesTheTexts() {
        String text = decision.toString();

        assertTrue(text.contains("NEEDS_CHANGES"), text);
        for (String hidden : List.of("Retake the kitchen", "Owner was away", "Roof not reached", REASON)) {
            assertFalse(text.contains(hidden), hidden);
        }
    }
}
