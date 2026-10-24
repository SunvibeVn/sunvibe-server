package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadOnlyReason;

import java.time.Instant;

/** A checklist of a VISITED appointment in the survey block (survey-checklist spec 7.2). */
@Schema(description = "A checklist of a VISITED appointment in the survey block")
public record ChecklistSummaryResponse(
        String id,
        int checklistNo,
        ChecklistStatus status,
        boolean editable,
        @Schema(nullable = true)
        ReadOnlyReason readOnlyReason,
        int missingCount,
        String holderUserId,
        @Schema(nullable = true)
        String holderName,
        Instant updatedAt,
        @Schema(nullable = true)
        String updatedByUserId,
        @Schema(nullable = true)
        String updatedByName) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSummaryResponse[" + id + "]";
    }
}
