package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - ChecklistStartRequest (Record)
 * <p>
 * Start a checklist on the current visit (survey-checklist spec 7.1): {@code intake} is null when the caller performed
 * the visit and required otherwise; the key itself is always sent.
 */
@Schema(description = "Start a survey checklist on the current visit")
public record ChecklistStartRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Null when the caller performed the visit, required otherwise")
        ChecklistIntakeRequest intake) {
}
