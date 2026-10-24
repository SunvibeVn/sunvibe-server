package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** A unit a correction covered, with the code its space had (survey-checklist spec 5.2). */
@Schema(description = "A unit a correction covered, with the code its space had")
public record ChecklistCorrectedUnit(
        String unitKey,
        @Schema(nullable = true)
        String spaceCode) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistCorrectedUnit[" + unitKey + "]";
    }
}
