package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.UsabilityReason;

/** Whether a PASSED revision can still be used (survey-checklist submission spec 4.7). */
@Schema(description = "Whether the PASSED revision can still be used")
public record ChecklistPassedResponse(
        boolean usable,
        @Schema(nullable = true)
        UsabilityReason reason,
        ChecklistSurveyedScope surveyedScope) {
}
