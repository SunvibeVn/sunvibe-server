package vn.sunvibe.survey.core.application.query.media;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.query.common.RefusedPhotoView;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * survey-checklist SV-310 pin: the reviewer's texts are private (survey BR-4, BR-14), so a frozen photo with its mark
 * never prints the mark's reason.
 */
class EvidenceViewTest {

    private static final String REASON = "The kitchen is out of focus";

    private final EvidenceEntry entry = new EvidenceEntry("M1", EvidenceCategory.KITCHEN, "S1", "P1", "full-sha");

    @Test
    void toString_hidesTheReason() {
        String text = new EvidenceView(entry, false, REASON, List.of()).toString();

        assertEquals("EvidenceView[M1]", text);
        assertFalse(text.contains(REASON));
    }

    /** survey-checklist SV-310 RC-6: the reasons of the refused photos with the same content are hidden too. */
    @Test
    void toString_hidesTheRefusedReasons() {
        RefusedPhotoView refused = new RefusedPhotoView("X", "R1", 1, EvidenceCategory.KITCHEN, "S1", "P1", REASON);

        String text = new EvidenceView(entry, null, null, List.of(refused)).toString();

        assertEquals("EvidenceView[M1]", text);
        assertFalse(text.contains(REASON));
    }
}
