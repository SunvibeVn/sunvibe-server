package vn.sunvibe.survey.core.domain.policy.media;

import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

/**
 * Whether a viewer can read a photo's content (survey-media spec 7.2, 4.2, A-23): {@code contentAvailable} is true
 * exactly when there is no unavailable reason.
 */
public final class MediaAvailability {

    private MediaAvailability() {
    }

    /**
     * The first reason of REMOVED, HELD, NOT_READY, CONTENT_DELETED, CONTENT_MISSING that applies, or null for a
     * servable photo; for a viewer who may hold photos (ADMIN) also null for a held photo whose content is otherwise
     * servable, removed or not (spec A-23).
     */
    public static MediaUnavailableReason unavailableReason(SurveyMedia media, boolean viewerCanHold) {
        if (viewerCanHold && media.isServableToAdmin()) {
            return null;
        }
        if (media.isRemoved()) {
            return MediaUnavailableReason.REMOVED;
        }
        if (media.isHeld()) {
            return MediaUnavailableReason.HELD;
        }
        if (media.getStatus() != MediaStatus.READY) {
            return MediaUnavailableReason.NOT_READY;
        }
        if (media.getContentDeletedAt() != null) {
            return MediaUnavailableReason.CONTENT_DELETED;
        }
        if (media.getContentMissingAt() != null) {
            return MediaUnavailableReason.CONTENT_MISSING;
        }
        return null;
    }
}
