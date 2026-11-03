package vn.sunvibe.survey.core.application.query;

import java.time.Instant;

import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;

/**
 * One photo as a viewer sees it (survey-media spec 7.2 {@code SurveyMediaItem}): no storage key, hash, upload key or
 * file name. The caption is null while it is hidden from this viewer (A-23); {@code held} carries only {@code at} for a
 * viewer who may not hold photos.
 */
public record MediaItemView(String id, int mediaNo, MediaStatus status, MediaStatusReason statusReasonOrNull,
                            String spaceIdOrNull, String spaceCodeAtUploadOrNull, EvidenceCategory category,
                            String captionOrNull, boolean captionHidden, String uploadedByUserId,
                            String uploadedByNameOrNull, Instant createdAt, Instant contentReceivedAtOrNull,
                            Instant readyAtOrNull, Integer widthOrNull, Integer heightOrNull, Removed removedOrNull,
                            Held heldOrNull, boolean contentAvailable,
                            MediaUnavailableReason unavailableReasonOrNull) {

    /** The removal: when, by whom, how, with the correction BR-12 asked for and the G1 save of a cascade. */
    public record Removed(Instant at, String byUserId, String byNameOrNull, RemovalKind kind,
                          MediaRemovalCorrection correctionOrNull, Long bySaveIdOrNull) {

        /** The correction is staff text: ids only. */
        @Override
        public String toString() {
            return "Removed[" + kind + " by " + byUserId + "]";
        }
    }

    /** The current hold (spec A-23); who and why only for a viewer who may hold photos. */
    public record Held(Instant at, String byUserIdOrNull, String byNameOrNull, HoldReason reasonOrNull) {
    }

    /** The caption and names are not logged: ids only. */
    @Override
    public String toString() {
        return "MediaItemView[" + id + " " + status + "]";
    }
}
