package vn.sunvibe.acquisition.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - LeadNoteResponse (Record)
 * <p>
 * Result of adding a note (acquisition-owner-lead-queue spec 6): the new history entry and the lead after it.
 */
@Schema(description = "The new history entry and the lead after the note")
public record LeadNoteResponse(LeadActivityResponse activity, OwnerLeadSummaryResponse lead) {

    /** The note and the contact name stay out of logs. */
    @Override
    public String toString() {
        return "LeadNoteResponse[hidden]";
    }
}
