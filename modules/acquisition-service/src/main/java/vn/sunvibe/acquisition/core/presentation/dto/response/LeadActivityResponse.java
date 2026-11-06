package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * DTO - LeadActivityResponse (Record)
 * <p>
 * One history entry of a lead (acquisition-owner-lead-queue spec 6). The actor is null for the system or the owner
 * who sent the form. Each name is the full name of the id before it; null when that id is null or has no user row.
 */
@Schema(description = "History entry of an owner lead")
public record LeadActivityResponse(
        OwnerLeadActivityType type,
        @Schema(description = "User id of the staff member; null for the system or the owner", nullable = true)
        String actorUserId,
        @Schema(description = "Full name of the staff member; null for the system or the owner, "
                + "or when the id has no user row", nullable = true)
        String actorName,
        @Schema(nullable = true) OwnerLeadStatus fromStatus,
        @Schema(nullable = true) OwnerLeadStatus toStatus,
        @Schema(nullable = true) String fromAssigneeId,
        @Schema(description = "Full name of the previous assignee; null when there was none "
                + "or the id has no user row", nullable = true)
        String fromAssigneeName,
        @Schema(nullable = true) String toAssigneeId,
        @Schema(description = "Full name of the new assignee; null when there is none "
                + "or the id has no user row", nullable = true)
        String toAssigneeName,
        @Schema(nullable = true) String note,
        @Schema(nullable = true) Instant nextActionAt,
        Instant occurredAt,
        @Schema(description = "The survey appointment (survey types) or survey checklist (checklist types) the entry "
                + "concerns; null when none", nullable = true)
        String referenceId,
        @Schema(description = "That appointment's or checklist's number within the lead; null when none",
                nullable = true)
        Integer referenceNo,
        @Schema(description = "The checklist group of a CHECKLIST_SAVED entry, the evidence category of a "
                + "CHECKLIST_MEDIA_ADDED or CHECKLIST_MEDIA_REMOVED entry, or the decision of a CHECKLIST_REVIEWED "
                + "entry (PASSED, or NEEDS_CHANGES or FAILED with its kind after a colon); null otherwise",
                nullable = true)
        String referenceDetail) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadActivityResponse[" + type + "]";
    }
}
