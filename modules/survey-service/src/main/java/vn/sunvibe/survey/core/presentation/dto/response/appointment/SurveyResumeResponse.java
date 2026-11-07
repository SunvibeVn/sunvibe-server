package vn.sunvibe.survey.core.presentation.dto.response.appointment;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.ResumeTarget;

/** Where a resume would take the lead (survey-checklist submission spec 7.2). */
@Schema(description = "Where a resume of the held lead would take it")
public record SurveyResumeResponse(
        @Schema(nullable = true, description = "Null when the lead cannot be resumed")
        ResumeTarget target) {
}
