package vn.sunvibe.survey.core.domain.policy.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.testsupport.MediaFixture;

/**
 * {@code SurveyMediaItem.unavailableReason} (survey-media spec 7.2, 4.2, A-23): the first that applies of REMOVED,
 * HELD, NOT_READY, CONTENT_DELETED, CONTENT_MISSING; none for a servable photo, and none for an ADMIN on a held photo
 * whose content is otherwise servable, removed or not.
 */
class MediaAvailabilityTest {

    @Test
    void unavailableReason_servable_none() {
        assertNull(MediaAvailability.unavailableReason(MediaFixture.ready(), false));
    }

    @Test
    void unavailableReason_notReady_forEveryStatusBeforeReady() {
        for (SurveyMedia media : new SurveyMedia[] {MediaFixture.awaiting(), MediaFixture.receiving(),
                MediaFixture.processing(), MediaFixture.rejected(), MediaFixture.failed(), MediaFixture.expired()}) {
            assertEquals(MediaUnavailableReason.NOT_READY, MediaAvailability.unavailableReason(media, false),
                    media.getStatus().name());
        }
    }

    @Test
    void unavailableReason_removedFirst_thenHeld() {
        SurveyMedia removedAndHeld = MediaFixture.held(MediaFixture.ready(), HoldReason.SECURITY_INCIDENT);
        removedAndHeld.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(400));

        assertEquals(MediaUnavailableReason.REMOVED, MediaAvailability.unavailableReason(removedAndHeld, false));
        assertEquals(MediaUnavailableReason.HELD, MediaAvailability.unavailableReason(
                MediaFixture.held(MediaFixture.processing(), HoldReason.SECURITY_INCIDENT), false));
        assertEquals(MediaUnavailableReason.REMOVED, MediaAvailability.unavailableReason(MediaFixture.cancelled(),
                false));
    }

    @Test
    void unavailableReason_admin_readsAHeldPhotoWithContent_removedOrNot() {
        SurveyMedia held = MediaFixture.held(MediaFixture.ready(), HoldReason.CREDENTIAL_EXPOSURE);
        assertNull(MediaAvailability.unavailableReason(held, true));
        held.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(400));
        assertNull(MediaAvailability.unavailableReason(held, true));

        SurveyMedia removed = MediaFixture.ready();
        removed.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(400));
        assertEquals(MediaUnavailableReason.REMOVED, MediaAvailability.unavailableReason(removed, true),
                "a removed photo that is not held is not readable to an ADMIN either");
        assertEquals(MediaUnavailableReason.HELD, MediaAvailability.unavailableReason(
                MediaFixture.held(MediaFixture.processing(), HoldReason.SECURITY_INCIDENT), true),
                "held and not READY: nothing to read");
    }

    @Test
    void unavailableReason_contentDeletedBeforeMissing() {
        SurveyMedia deleted = MediaFixture.ready();
        deleted.markContentMissing(MediaFixture.T0.plusSeconds(500));
        assertEquals(MediaUnavailableReason.CONTENT_MISSING, MediaAvailability.unavailableReason(deleted, false));

        SurveyMedia both = MediaFixture.ready();
        both.remove(RemovalKind.HOLDER, MediaFixture.SALE, null, null, MediaFixture.T0.plusSeconds(400));
        both.markContentDeleted(MediaFixture.T0.plusSeconds(500));
        assertEquals(MediaUnavailableReason.REMOVED, MediaAvailability.unavailableReason(both, false));
    }
}
