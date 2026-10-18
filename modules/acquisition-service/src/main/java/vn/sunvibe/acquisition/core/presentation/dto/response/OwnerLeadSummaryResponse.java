package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * DTO - OwnerLeadSummaryResponse (Record)
 * <p>
 * One lead as the staff queue lists it (acquisition-owner-lead-queue spec 6, C-15): contact name and ward, but no
 * phone, email, address or position.
 */
@Schema(description = "Owner lead in the staff queue (no phone, email, address or coordinates)")
public record OwnerLeadSummaryResponse(
        String id,
        @Schema(example = "SV-7K3M-Q9TX") String requestCode,
        OwnerLeadStatus status,
        OwnerLeadIntent intent,
        String contactName,
        @Schema(example = "48") String provinceCode,
        @Schema(example = "20242") String wardCode,
        String wardName,
        OwnerLeadPropertyType propertyType,
        @Schema(description = "User id of the assignee; null when nobody holds the lead") String assignedUserId,
        @Schema(description = "Full name of the assignee; null when nobody holds the lead "
                + "or the id has no user row", nullable = true)
        String assignedUserName,
        Instant nextActionAt,
        @Schema(description = "True when the lead has at least one possible duplicate the caller can see")
        boolean possibleDuplicate,
        Instant createdAt,
        @Schema(description = "Send it back as expectedVersion with the next change") long version) {

    /** The contact name is personal data: never print it. */
    @Override
    public String toString() {
        return "OwnerLeadSummaryResponse[" + id + "]";
    }
}
