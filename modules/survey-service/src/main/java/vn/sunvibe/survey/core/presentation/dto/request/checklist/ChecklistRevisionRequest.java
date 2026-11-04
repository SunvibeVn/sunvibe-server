package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - ChecklistRevisionRequest (Record)
 * <p>
 * Start the next revision of a visit (survey-checklist submission spec 4.6, 7.1, A-7).
 */
@Schema(description = "Start the next revision of a visit returned for documents")
public record ChecklistRevisionRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Null when the caller held the previous revision, required otherwise")
        ChecklistIntakeRequest intake) {
}
