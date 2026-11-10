package vn.sunvibe.survey.core.presentation.converter;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.query.media.MediaItemView;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The survey-media item answer (survey-media spec 7.2): ids are opaque strings in the API (ADR-006), so the G1 save
 * of a CASCADE removal keeps every digit where a JavaScript number would round it (SV-244 spec AC-2).
 */
class MediaResponseConverterTest {

    private final MediaResponseConverter converter = new MediaResponseConverter();

    @Test
    void toItem_cascadeSaveIdAbove2To53_isItsExactDecimalString() {
        long aboveDoublePrecision = 9007199254740993L; // 2^53 + 1: an IEEE double cannot hold it
        MediaItemView view = readyPhotoRemovedBy(new MediaItemView.Removed(Instant.parse("2026-11-10T03:00:00Z"),
                "U1", null, RemovalKind.CASCADE, null, aboveDoublePrecision));

        assertEquals("9007199254740993", converter.toItem(view).removed().bySaveId());
    }

    private static MediaItemView readyPhotoRemovedBy(MediaItemView.Removed removed) {
        Instant created = Instant.parse("2026-11-10T02:00:00Z");
        return new MediaItemView("M1", 1, MediaStatus.READY, null, null, null, EvidenceCategory.DETAIL, null, false,
                "U1", null, created, created, created, 1600, 1200, removed, null, false,
                MediaUnavailableReason.REMOVED, null);
    }
}
