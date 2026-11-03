package vn.sunvibe.survey.core.domain.model.media;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The security hold of a photo (survey-media spec A-23): held is defined once, a release nulls nothing. */
class MediaHoldTest {

    private static final Instant T0 = Instant.parse("2026-11-01T02:00:00Z");
    private static final String ADMIN = "01JADM1N000000000000000001";

    @Test
    void none_isNotHeldAndHidesNothing() {
        assertFalse(MediaHold.NONE.isHeld());
        assertFalse(MediaHold.NONE.captionHidden(false));
        assertNull(MediaHold.NONE.heldAt());
    }

    @Test
    void held_isHeldUntilReleased() {
        MediaHold held = MediaHold.NONE.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0);

        assertTrue(held.isHeld());
        assertFalse(held.release(ADMIN, T0.plusSeconds(60)).isHeld());
    }

    @Test
    void captionHidden_whileHeld_forViewersWithoutHold() {
        MediaHold held = MediaHold.NONE.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0);

        assertTrue(held.captionHidden(false));
        assertFalse(held.captionHidden(true));
        assertFalse(held.release(ADMIN, T0.plusSeconds(60)).captionHidden(false));
    }

    @Test
    void captionHidden_credentialExposureAfterRelease_staysHidden() {
        MediaHold released = MediaHold.NONE.hold(ADMIN, HoldReason.CREDENTIAL_EXPOSURE, T0)
                .release(ADMIN, T0.plusSeconds(60));

        assertFalse(released.isHeld());
        assertTrue(released.captionHidden(false));
        assertFalse(released.captionHidden(true));
    }

    @Test
    void releaseAt_beforeHeldAt_usesHeldAt() {
        MediaHold held = MediaHold.NONE.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0);

        MediaHold released = held.release(ADMIN, T0.minusSeconds(5));

        assertEquals(T0, released.releasedAt());
        assertEquals(ADMIN, released.releasedByUserId());
    }

    @Test
    void release_keepsTheHoldColumns() {
        MediaHold released = MediaHold.NONE.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0)
                .release("01JADM1N000000000000000002", T0.plusSeconds(60));

        assertEquals(T0, released.heldAt());
        assertEquals(ADMIN, released.heldByUserId());
        assertEquals(HoldReason.SECURITY_INCIDENT, released.reason());
    }

    @Test
    void hold_afterRelease_clearsTheReleasePair() {
        MediaHold again = MediaHold.NONE.hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0)
                .release(ADMIN, T0.plusSeconds(60)).hold(ADMIN, HoldReason.SECURITY_INCIDENT, T0.plusSeconds(120));

        assertTrue(again.isHeld());
        assertNull(again.releasedAt());
        assertNull(again.releasedByUserId());
        assertEquals(T0.plusSeconds(120), again.heldAt());
    }
}
