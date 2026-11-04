package vn.sunvibe.survey.core.presentation.dto.response.media;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.MediaUnavailableReason;

/**
 * One survey photo (survey-media spec 7.2 {@code SurveyMediaItem}): no storage key, hash, upload key or file name.
 */
@Schema(description = "A survey photo as the viewer may see it")
public record SurveyMediaItem(
        String id,
        int mediaNo,
        MediaStatus status,
        @Schema(nullable = true, description = "The reason of a final status other than READY")
        MediaStatusReason statusReason,
        @Schema(nullable = true, description = "The G1 space of the photo; null for the whole asset")
        String spaceId,
        @Schema(nullable = true, description = "The space's code when the photo was created")
        String spaceCodeAtUpload,
        EvidenceCategory category,
        @Schema(nullable = true, description = "Null when absent or hidden from this viewer (captionHidden)")
        String caption,
        boolean captionHidden,
        String uploadedByUserId,
        @Schema(nullable = true)
        String uploadedByName,
        Instant createdAt,
        @Schema(nullable = true)
        Instant contentReceivedAt,
        @Schema(nullable = true)
        Instant readyAt,
        @Schema(nullable = true, description = "Of the FULL image, once READY")
        Integer width,
        @Schema(nullable = true, description = "Of the FULL image, once READY")
        Integer height,
        @Schema(nullable = true)
        SurveyMediaRemoval removed,
        @Schema(nullable = true, description = "While an ADMIN holds the photo; only `at` for a viewer who may not "
                + "hold photos")
        SurveyMediaHold held,
        boolean contentAvailable,
        @Schema(nullable = true, description = "Why the content cannot be read now; null when contentAvailable")
        MediaUnavailableReason unavailableReason,
        @Schema(nullable = true, description = "The number of the revision a carried photo came from; null for a "
                + "photo uploaded into this revision")
        Integer carriedFromChecklistNo) {

    /** Staff text: ids only. */
    @Override
    public String toString() {
        return "SurveyMediaItem[" + id + " " + status + "]";
    }
}
