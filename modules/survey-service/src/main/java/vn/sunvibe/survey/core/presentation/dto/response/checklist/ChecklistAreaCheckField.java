package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;

/** Area check field of a survey checklist, as read (survey-checklist spec 7.2). */
@Schema(description = "Area check field of a survey checklist, as read")
public record ChecklistAreaCheckField(
        FieldState state,
        @Schema(nullable = true, description = "Present exactly when state is VALUE")
        ChecklistAreaCheck value,
        @Schema(nullable = true, description = "Present exactly when state is NOT_APPLICABLE, 1..500 characters")
        String naReason,
        @Schema(nullable = true, description = "Null until the unit is first changed")
        ChecklistUnitMeta meta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAreaCheckField[" + state + "]";
    }
}
