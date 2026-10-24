package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer of a takeover (survey-checklist spec 7.1). */
@Schema(description = "The answer of a takeover")
public record ChecklistTakenOverResponse(
        ChecklistResponse checklist,
        ChecklistHistoryEntryResponse historyEntry) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTakenOverResponse[" + checklist + "]";
    }
}
