package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether the viewer can withdraw the current PASSED (property-create requalification spec 7, S-12). */
@Schema(description = "Whether the viewer can withdraw the lead's current PASSED revision")
public record ChecklistRequalificationStateResponse(
        boolean allowed,
        @Schema(nullable = true, description = "The revision to mark; null when not allowed")
        String checklistId,
        @Schema(nullable = true, description = "The version the command names; null when not allowed")
        Long expectedVersion) {
}
