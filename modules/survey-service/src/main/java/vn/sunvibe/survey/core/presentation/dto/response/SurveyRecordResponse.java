package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistPassedResponse;

/** The survey record of the lead's current cycle (survey-checklist submission spec 4.7, 7.2). */
@Schema(description = "The survey record of the lead's current intent cycle")
public record SurveyRecordResponse(
        boolean hasPassed,
        boolean hasSubmitted,
        @Schema(nullable = true, description = "With a PASSED revision: whether it can still be used")
        ChecklistPassedResponse passed) {
}
