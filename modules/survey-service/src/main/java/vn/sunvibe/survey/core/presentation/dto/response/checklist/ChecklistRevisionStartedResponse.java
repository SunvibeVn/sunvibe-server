package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer of a new revision (survey-checklist submission spec 7.1). */
@Schema(description = "The answer of a new revision")
public record ChecklistRevisionStartedResponse(
        ChecklistResponse checklist,
        ChecklistHistoryEntryResponse historyEntry) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistRevisionStartedResponse[" + checklist + "]";
    }
}
