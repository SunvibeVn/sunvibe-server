package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The metadata of the layout note of a space (survey-checklist spec A-26). */
@Schema(description = "The metadata of the layout note of a space")
public record ChecklistDimensionsFieldMeta(
        @Schema(nullable = true)
        ChecklistUnitMeta layoutNote) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDimensionsFieldMeta[hidden]";
    }
}
