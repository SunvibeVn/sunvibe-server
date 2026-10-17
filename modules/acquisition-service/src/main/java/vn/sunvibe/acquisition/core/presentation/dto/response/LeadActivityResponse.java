package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * DTO - LeadActivityResponse (Record)
 * <p>
 * One history entry of a lead (acquisition-owner-lead-queue spec 6). The actor is null for the system or the owner
 * who sent the form.
 */
@Schema(description = "History entry of an owner lead")
public record LeadActivityResponse(
        OwnerLeadActivityType type,
        @Schema(description = "User id of the staff member; null for the system or the owner") String actorUserId,
        OwnerLeadStatus fromStatus,
        OwnerLeadStatus toStatus,
        String fromAssigneeId,
        String toAssigneeId,
        String note,
        Instant nextActionAt,
        Instant occurredAt) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "LeadActivityResponse[" + type + "]";
    }
}
