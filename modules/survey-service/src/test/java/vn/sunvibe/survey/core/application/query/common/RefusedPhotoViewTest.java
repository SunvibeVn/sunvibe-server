package vn.sunvibe.survey.core.application.query.common;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** survey-checklist SV-310: the reviewer's reason of a refused photo is private (survey BR-4, BR-14). */
class RefusedPhotoViewTest {

    private static final String REASON = "The kitchen is out of focus";

    @Test
    void toString_hidesTheReason() {
        String text = new RefusedPhotoView("X", "R1", 1, EvidenceCategory.KITCHEN, "S1", "P1", REASON).toString();

        assertTrue(text.contains("X"), text);
        assertFalse(text.contains(REASON), text);
    }
}
