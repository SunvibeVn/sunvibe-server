package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/**
 * survey-checklist SV-310: a photo a decision on an earlier revision of the visit did not accept: the photo, the
 * revision that refused it, its placement in that revision's frozen evidence and the reviewer's reason.
 */
@Schema(description = "A photo a decision on an earlier revision of the visit did not accept")
public record ChecklistRefusedPhoto(
        String mediaId,
        @Schema(description = "The revision whose decision did not accept the photo")
        String checklistId,
        @Schema(description = "The number of that revision")
        int checklistNo,
        @Schema(description = "The photo's category in that revision's frozen evidence")
        EvidenceCategory category,
        @Schema(nullable = true, description = "The photo's space in that revision's frozen evidence; null for the"
                + " asset")
        String spaceId,
        @Schema(nullable = true)
        String spaceCode,
        @Schema(description = "The reviewer's reason for not accepting the photo")
        String reason) {

    /** The reviewer's text is private: never print it. */
    @Override
    public String toString() {
        return "ChecklistRefusedPhoto[" + mediaId + "]";
    }
}
