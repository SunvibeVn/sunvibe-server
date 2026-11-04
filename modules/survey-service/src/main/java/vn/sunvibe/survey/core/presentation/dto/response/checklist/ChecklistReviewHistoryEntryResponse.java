package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The lead History entry of a decision (survey-checklist submission spec 4.9): the fields of the lead's activity
 * response with the lead's move and the outcome and kind; it never carries the reviewer's text.
 */
@Schema(description = "The lead History entry written by a decision")
public record ChecklistReviewHistoryEntryResponse(
        @Schema(allowableValues = {"CHECKLIST_REVIEWED"})
        String type,
        String actorUserId,
        @Schema(nullable = true, description = "Full name of the staff member; null when the id has no user row")
        String actorName,
        @Schema(nullable = true, description = "Null when the lead kept its status")
        String fromStatus,
        @Schema(nullable = true, description = "Null when the lead kept its status")
        String toStatus,
        Instant occurredAt,
        @Schema(description = "The checklist")
        String referenceId,
        @Schema(description = "The checklist number")
        Integer referenceNo,
        @Schema(description = "The outcome and kind: PASSED, NEEDS_CHANGES:<kind> or FAILED:<kind>")
        String referenceDetail) {
}
