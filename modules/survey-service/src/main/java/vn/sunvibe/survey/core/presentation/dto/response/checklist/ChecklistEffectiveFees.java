package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The fees a space gets: the common list and its own (survey-checklist spec 7.2). */
@Schema(description = "The fees a space gets: the common list and its own")
public record ChecklistEffectiveFees(
        ChecklistFeeListTerm common,
        @Schema(nullable = true)
        ChecklistFeeListTerm space) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistEffectiveFees[hidden]";
    }
}
