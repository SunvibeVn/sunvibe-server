package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

import java.util.List;

/**
 * One frozen photo of a submission with the reviewer's mark (survey-checklist submission spec 7.2) and the photos an
 * earlier revision's decision on the visit did not accept with the same content (survey-checklist SV-310).
 */
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
        String rejectionReason,
        @Schema(description = "The photos that a decision on an earlier revision of the visit did not accept and whose"
                + " source content is the same as this photo's, one per refusing decision, newest revision first;"
                + " empty when none. It informs the reviewer and blocks no decision")
        List<ChecklistRefusedPhoto> refusedSameContent) {

    /** The reviewer's text is private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSubmittedEvidence[" + mediaId + "]";
    }
}
