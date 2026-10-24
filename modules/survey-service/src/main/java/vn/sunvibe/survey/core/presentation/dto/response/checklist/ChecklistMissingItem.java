package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.policy.checklist.MissingProblem;

/** A known gap before a full-assessment submission (survey-checklist spec 4.7). */
@Schema(description = "A known gap before a full-assessment submission")
public record ChecklistMissingItem(
        ChecklistGroup group,
        @Schema(nullable = true)
        String spaceId,
        @Schema(nullable = true)
        String field,
        MissingProblem problem) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistMissingItem[" + group + ", " + problem + "]";
    }
}
