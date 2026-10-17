package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * DTO - ChangeLeadStatusRequest (Record)
 * <p>
 * Move a lead to another status (acquisition-owner-lead-queue spec 6), guarded by the version the caller last saw.
 * Whether a reason or a duplicate target is needed depends on the target status and is checked by the domain on the
 * same fields. Messages are English and never repeat the value.
 */
@Schema(description = "Move an owner lead to another status")
public record ChangeLeadStatusRequest(
        @NotNull(message = "Target status is required.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OwnerLeadStatus toStatus,

        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must be 0 or more.")
        @Schema(description = "The lead version the caller last saw", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedVersion,

        @Size(max = 500, message = "Reason must be at most 500 characters.")
        @Schema(description = "Required to close the lead (DUPLICATE, REJECTED, WITHDRAWN)", maxLength = 500)
        String reason,

        // No length rule here: every refused target, an over-long one too, gets the one message of the duplicate
        // target rule (acquisition-owner-lead-queue C-17), which only looks up a well-formed 26-character lead id
        @Schema(description = "Required for DUPLICATE: id of the lead this one repeats")
        String duplicateOfLeadId,

        @Size(max = 2000, message = "Note must be at most 2000 characters.")
        @Schema(maxLength = 2000)
        String note,

        @StorableInstant(message = "Next action time must be from 1970-01-01T00:00:00Z to the end of year 9999.")
        @Schema(description = "Optional; when the next action on the lead is due, from 1970-01-01T00:00:00Z to the "
                + "end of year 9999")
        Instant nextActionAt) {

    /** Reason and note are staff text: never print them. */
    @Override
    public String toString() {
        return "ChangeLeadStatusRequest[hidden]";
    }
}
