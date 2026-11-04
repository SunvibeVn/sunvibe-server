package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/** One frozen photo of a submission with the reviewer's mark (survey-checklist submission spec 7.2). */
@Schema(description = "A frozen photo of a submission")
public record ChecklistSubmittedEvidence(
        String mediaId,
        EvidenceCategory category,
        @Schema(nullable = true)
        String spaceId,
        @Schema(nullable = true)
        String spaceCode,
        String fullSha256,
        @Schema(nullable = true, description = "Null before a decision; after it, false for a photo the reviewer "
                + "did not accept")
        Boolean accepted,
        @Schema(nullable = true)
        String rejectionReason) {

    /** The reviewer's text is private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSubmittedEvidence[" + mediaId + "]";
    }
}
