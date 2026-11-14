package vn.sunvibe.survey.core.domain.policy.media;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a revision holds: its own rows and the carried photos it still selects (submission spec 4.6, A-12). */
class RevisionSelectionTest {

    private static final String REVISION = "01JCHECK000000000000000002";
    private static final String ORIGIN = "01JCHECK000000000000000001";
    private static final Instant AT = Instant.parse("2026-11-04T02:00:00Z");

    private static MediaSelection carried(SurveyMediaId id) {
        return MediaSelection.carried(REVISION, id, ORIGIN, AT);
    }

    @Test
    void selectedIds_leaveOutDeselectedPhotos() {
        SurveyMediaId kept = MediaFixture.newId();
        SurveyMediaId left = MediaFixture.newId();

        assertEquals(List.of(kept), RevisionSelection.selectedIds(List.of(carried(kept),
                carried(left).deselected("01JSA1E0000000000000000001", RemovalKind.HOLDER, null, null, AT))));
    }

    @Test
    void photos_ownFirstThenCarried() {
        SurveyMedia own = MediaFixture.ready();
        SurveyMedia carried = MediaFixture.ready();

        assertEquals(List.of(own, carried), RevisionSelection.photos(List.of(own), List.of(carried)));
    }

    @Test
    void carriedFrom_namesTheNumberOfTheOrigin() {
        SurveyMediaId id = MediaFixture.newId();

        assertEquals(Map.of(id.getValue(), 3), RevisionSelection.carriedFromNumbers(List.of(carried(id)),
                Map.of(ORIGIN, 3)));
    }

    @Test
    void deselected_isNoLongerSelected() {
        MediaSelection selection = carried(MediaFixture.newId());

        assertTrue(selection.isSelected());
        assertFalse(selection.deselected("01JSA1E0000000000000000001", RemovalKind.HOLDER, null, null, AT).isSelected());
    }

    @Test
    void deselected_twice_refused() {
        MediaSelection left = carried(MediaFixture.newId()).deselected("01JSA1E0000000000000000001",
                RemovalKind.HOLDER, null, null, AT);

        assertThrows(IllegalStateException.class,
                () -> left.deselected("01JSA1E0000000000000000001", RemovalKind.HOLDER, null, null, AT));
    }

    /** survey-checklist SV-211: a photo a reviewer of the visit did not accept stays in the earlier revision. */
    @Test
    void photosToCarry_leavesOutRejectedPhotos() {
        SurveyMedia a = MediaFixture.ready();
        SurveyMedia b = MediaFixture.ready();

        assertEquals(List.of(a), RevisionSelection.photosToCarry(List.of(a, b), Set.of(b.getId().getValue())));
    }

    @Test
    void photosToCarry_leavesOutPhotosThatCannotBeServed() {
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);
        SurveyMedia kept = MediaFixture.ready();
        SurveyMedia processing = MediaFixture.processing();

        assertEquals(List.of(kept), RevisionSelection.photosToCarry(List.of(held, kept, processing), Set.of()));
    }

    @Test
    void photosToCarry_keepsTheOrderOfTheSelection() {
        SurveyMedia a = MediaFixture.ready();
        SurveyMedia b = MediaFixture.ready();

        assertEquals(List.of(b, a), RevisionSelection.photosToCarry(List.of(b, a), Set.of()));
    }
}
