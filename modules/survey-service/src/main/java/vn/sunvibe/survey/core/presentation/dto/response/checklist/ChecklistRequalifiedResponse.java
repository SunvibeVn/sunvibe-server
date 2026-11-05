package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyLeadStateResponse;

/** The answer of a requalification (property-create requalification spec 7). */
@Schema(description = "The answer of a requalification")
public record ChecklistRequalifiedResponse(
        ChecklistResponse checklist,
        SurveyLeadStateResponse lead,
        ChecklistRequalificationHistoryEntryResponse historyEntry) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistRequalifiedResponse[" + checklist + "]";
    }
}
