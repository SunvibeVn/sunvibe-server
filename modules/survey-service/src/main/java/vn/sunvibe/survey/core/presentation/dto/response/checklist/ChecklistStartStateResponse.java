package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.StartBlock;

/** Whether the viewer can start a checklist on the current visit (survey-checklist spec 7.2). */
@Schema(description = "Whether the viewer can start a checklist on the current visit")
public record ChecklistStartStateResponse(
        boolean allowed,
        @Schema(nullable = true)
        String visitAppointmentId,
        boolean intakeDeclarationRequired,
        @Schema(nullable = true, description = "The first block that applies, null when allowed")
        StartBlock blockedBy) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistStartStateResponse[" + allowed + ", " + blockedBy + "]";
    }
}
