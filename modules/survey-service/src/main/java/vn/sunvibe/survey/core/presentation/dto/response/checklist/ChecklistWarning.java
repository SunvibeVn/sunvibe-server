package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.WarningType;

/** A warning of the preview (survey-checklist spec 4.7). */
@Schema(description = "A warning of the preview")
public record ChecklistWarning(
        WarningType type,
        @Schema(nullable = true)
        String hazardId,
        @Schema(nullable = true)
        String checklistId,
        @Schema(nullable = true)
        Integer checklistNo,
        @Schema(nullable = true)
        String spaceId) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistWarning[" + type + "]";
    }
}
