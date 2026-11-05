package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The lead History entry of a requalification (property-create requalification spec 7): the event with the lead's move
 * and the checklist; it never carries the manager's text.
 */
@Schema(description = "The lead History entry written by a requalification")
public record ChecklistRequalificationHistoryEntryResponse(
        @Schema(allowableValues = {"REQUALIFICATION_MARKED"})
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
        Integer referenceNo) {
}
