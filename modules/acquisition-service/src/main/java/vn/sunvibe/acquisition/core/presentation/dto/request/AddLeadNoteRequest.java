package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO - AddLeadNoteRequest (Record)
 * <p>
 * Record a note on a lead (acquisition-owner-lead-queue spec 6), optionally with the time of the next action.
 * Messages are English and never repeat the value.
 */
@Schema(description = "Add a note to an owner lead")
public record AddLeadNoteRequest(
        @NotBlank(message = "Note is required.")
        @Size(max = 2000, message = "Note must be at most 2000 characters.")
        @Schema(minLength = 1, maxLength = 2000, requiredMode = Schema.RequiredMode.REQUIRED)
        String note,

        @StorableInstant(message = "Next action time must be from 1970-01-01T00:00:00Z to the end of year 9999.")
        @Schema(description = "Optional; when the next action on the lead is due (left out keeps the current one), "
                + "from 1970-01-01T00:00:00Z to the end of year 9999")
        Instant nextActionAt) {

    /** The note is staff text: never print it. */
    @Override
    public String toString() {
        return "AddLeadNoteRequest[hidden]";
    }
}
