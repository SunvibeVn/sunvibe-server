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
        @Schema(description = "User id of the staff member; null for the system or the owner") String actorUserId,
        @Schema(description = "Full name of the staff member; null for the system or the owner, "
                + "or when the id has no user row", nullable = true)
        String actorName,
        OwnerLeadStatus fromStatus,
        OwnerLeadStatus toStatus,
        String fromAssigneeId,
        @Schema(description = "Full name of the previous assignee; null when there was none "
                + "or the id has no user row", nullable = true)
        String fromAssigneeName,
        String toAssigneeId,
        @Schema(description = "Full name of the new assignee; null when there is none "
                + "or the id has no user row", nullable = true)
        String toAssigneeName,
        String note,
        Instant nextActionAt,
        Instant occurredAt,
        @Schema(description = "The survey appointment the entry concerns (survey-schedule); null when none",
                nullable = true)
        String referenceId,
        @Schema(description = "That appointment's number within the lead; null when none", nullable = true)
        Integer referenceNo) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadActivityResponse[" + type + "]";
    }
}
