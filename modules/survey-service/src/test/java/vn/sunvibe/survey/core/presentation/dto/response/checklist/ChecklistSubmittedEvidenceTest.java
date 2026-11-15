package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * survey-checklist SV-310 pin: the reviewer's texts are private (survey BR-4, BR-14), so a frozen photo of the answer
 * never prints the mark's reason.
 */
class ChecklistSubmittedEvidenceTest {

    private static final String REASON = "The kitchen is out of focus";

    @Test
    void toString_hidesTheReason() {
        String text = new ChecklistSubmittedEvidence("M1", EvidenceCategory.KITCHEN, "S1", "P1", "full-sha", false,
                REASON).toString();

        assertEquals("ChecklistSubmittedEvidence[M1]", text);
        assertFalse(text.contains(REASON));
    }
}
