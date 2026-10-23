package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** The lead after a survey command: its status, new version and latest activity time. */
@Schema(description = "The owner lead after a survey command")
public record SurveyLeadStateResponse(
        String id,
        @Schema(allowableValues = {"NEW", "VALIDATING", "ASSIGNED", "CONTACTED", "SURVEY_SCHEDULED", "QUALIFIED",
                "AGREEMENT_PENDING", "ONBOARDED", "ON_HOLD", "DUPLICATE", "REJECTED", "WITHDRAWN"})
        String status,
        long version,
        Instant lastActivityAt) {
}
