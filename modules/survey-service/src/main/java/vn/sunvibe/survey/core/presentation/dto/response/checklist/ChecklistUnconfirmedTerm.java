package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

/** A rental term or availability date still not confirmed (survey-checklist spec 4.7). */
@Schema(description = "A rental term or availability date still not confirmed")
public record ChecklistUnconfirmedTerm(
        ChecklistGroup group,
        @Schema(nullable = true)
        String spaceId,
        String field) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistUnconfirmedTerm[" + group + "]";
    }
}
