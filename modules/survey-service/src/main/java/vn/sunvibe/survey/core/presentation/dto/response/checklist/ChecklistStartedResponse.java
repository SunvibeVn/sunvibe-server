package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyLeadStateResponse;

/** The answer of a checklist start (survey-checklist spec 7.1). */
@Schema(description = "The answer of a checklist start")
public record ChecklistStartedResponse(
        ChecklistResponse checklist,
        SurveyLeadStateResponse lead,
        ChecklistHistoryEntryResponse historyEntry) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistStartedResponse[" + checklist + "]";
    }
}
