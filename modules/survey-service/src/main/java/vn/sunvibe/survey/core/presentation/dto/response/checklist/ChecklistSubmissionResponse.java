package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** The facts of a submission (survey-checklist submission spec 7.2). */
@Schema(description = "Who submitted the revision, when, and its frozen photos")
public record ChecklistSubmissionResponse(
        ChecklistStaffMember submittedBy,
        Instant submittedAt,
        @Schema(description = "The photo policy in force at the submission")
        String mediaPolicy,
        @Schema(description = "SHA-256 of the canonical groups and evidence")
        String contentSha256,
        List<ChecklistSubmittedEvidence> evidence) {

    @Override
    public String toString() {
        return "ChecklistSubmissionResponse[hidden]";
    }
}
