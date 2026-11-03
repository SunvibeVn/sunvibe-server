package vn.sunvibe.survey.core.domain.model.media;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The media set of a checklist (survey-media spec 4.1, 5.2): numbers photos and versions what readers see. */
class MediaSetTest {

    private static final Instant T0 = Instant.parse("2026-11-01T02:00:00Z");
    private static final String CHECKLIST = "01JCHECK1157000000000000A1";

    @Test
    void start_isEmptyAtVersionZero() {
        MediaSet set = MediaSet.start(CHECKLIST, T0);

        assertEquals(CHECKLIST, set.getChecklistId());
        assertEquals(0L, set.getSetVersion());
        assertEquals(0, set.getLastMediaNo());
        assertEquals(T0, set.getUpdatedAt());
    }

    @Test
    void nextMediaNo_countsUpWithoutGapsInOneSet() {
        MediaSet set = MediaSet.start(CHECKLIST, T0);

        assertEquals(1, set.nextMediaNo());
        assertEquals(2, set.nextMediaNo());
        assertEquals(2, set.getLastMediaNo());
    }

    @Test
    void bump_raisesTheVersionAndTime() {
        MediaSet set = MediaSet.load(CHECKLIST, 41L, 7, T0);

        set.bump(T0.plusSeconds(5));

        assertEquals(42L, set.getSetVersion());
        assertEquals(7, set.getLastMediaNo());
        assertEquals(T0.plusSeconds(5), set.getUpdatedAt());
    }

    @Test
    void load_negativeValues_areRefused() {
        assertThrows(IllegalArgumentException.class, () -> MediaSet.load(CHECKLIST, -1L, 0, T0));
        assertThrows(IllegalArgumentException.class, () -> MediaSet.load(CHECKLIST, 0L, -1, T0));
        assertThrows(IllegalArgumentException.class, () -> MediaSet.load(null, 0L, 0, T0));
    }
}
