package vn.sunvibe.survey.core.presentation.dto.response.media;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHistoryEntryResponse;

/**
 * The answer of a photo content upload (survey-media spec 7.1, 202): the photo, the set version after the change and
 * the History entry the commit wrote; null for a replay of the same bytes.
 */
@Schema(description = "The photo after a change: its bytes received or its removal")
public record SurveyMediaContentResponse(
        SurveyMediaItem media,
        long setVersion,
        @Schema(nullable = true, description = "CHECKLIST_MEDIA_ADDED for received bytes, CHECKLIST_MEDIA_REMOVED for"
                + " the removal of a processing or ready photo; null for a replay, a repeated removal or a removal"
                + " that only tidies the list")
        ChecklistHistoryEntryResponse historyEntry) {
}
