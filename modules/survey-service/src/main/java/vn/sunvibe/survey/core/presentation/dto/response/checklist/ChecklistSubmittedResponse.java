package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer of a submit (survey-checklist submission spec 7.1). */
@Schema(description = "The answer of a submit")
public record ChecklistSubmittedResponse(
        ChecklistResponse checklist,
        @Schema(nullable = true, description = "Null when the same caller repeats a submit: nothing was written")
        ChecklistHistoryEntryResponse historyEntry) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSubmittedResponse[" + checklist + "]";
    }
}
