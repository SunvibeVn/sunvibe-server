package vn.sunvibe.survey.core.presentation.dto.response.media;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;

/** The current ADMIN hold of a survey photo (survey-media spec 7.2, A-23). */
@Schema(description = "The current hold of a survey photo; who and why only for a viewer who may hold photos")
public record SurveyMediaHold(
        Instant at,
        @Schema(nullable = true)
        String byUserId,
        @Schema(nullable = true)
        String byName,
        @Schema(nullable = true)
        HoldReason reason) {
}
