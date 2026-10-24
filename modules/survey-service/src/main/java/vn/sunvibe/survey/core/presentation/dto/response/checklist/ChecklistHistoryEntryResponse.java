package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The lead History entry a checklist command wrote (survey-checklist spec 7.2): the fields of the lead's
 * {@code LeadActivityResponse}, with the type limited to the three checklist types; it never carries text, so the
 * status, assignee, note and next-action fields are always null.
 */
@Schema(description = "The lead History entry written by a checklist command")
public record ChecklistHistoryEntryResponse(
        @Schema(allowableValues = {"CHECKLIST_STARTED", "CHECKLIST_TAKEN_OVER", "CHECKLIST_SAVED"})
        String type,
        String actorUserId,
        @Schema(nullable = true, description = "Full name of the staff member; null when the id has no user row")
        String actorName,
        @Schema(nullable = true, description = "Always null: a checklist command keeps the lead's status")
        String fromStatus,
        @Schema(nullable = true, description = "Always null: a checklist command keeps the lead's status")
        String toStatus,
        @Schema(nullable = true, description = "Always null")
        String fromAssigneeId,
        @Schema(nullable = true, description = "Always null")
        String fromAssigneeName,
        @Schema(nullable = true, description = "Always null")
        String toAssigneeId,
        @Schema(nullable = true, description = "Always null")
        String toAssigneeName,
        @Schema(nullable = true, description = "Always null: checklist History entries carry no text")
        String note,
        @Schema(nullable = true, description = "Always null")
        Instant nextActionAt,
        Instant occurredAt,
        @Schema(description = "The checklist")
        String referenceId,
        @Schema(description = "The checklist number")
        Integer referenceNo,
        @Schema(nullable = true, description = "The group of a CHECKLIST_SAVED entry; null otherwise")
        String referenceDetail) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistHistoryEntryResponse[" + type + "]";
    }
}
