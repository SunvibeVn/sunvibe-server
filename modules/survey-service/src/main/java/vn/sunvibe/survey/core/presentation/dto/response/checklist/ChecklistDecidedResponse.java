package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.common.SurveyLeadStateResponse;

/** The answer of a decision (survey-checklist submission spec 7.1). */
@Schema(description = "The answer of a decision")
public record ChecklistDecidedResponse(
        ChecklistResponse checklist,
        SurveyLeadStateResponse lead,
        ChecklistReviewHistoryEntryResponse historyEntry) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDecidedResponse[" + checklist + "]";
    }
}
