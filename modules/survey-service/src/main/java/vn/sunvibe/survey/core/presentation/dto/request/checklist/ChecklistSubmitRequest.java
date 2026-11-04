package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - ChecklistSubmitRequest (Record)
 * <p>
 * Submit a revision for review (survey-checklist submission spec 7.1, A-6): the checklist version the caller read.
 */
@Schema(description = "Submit a survey checklist revision for review")
public record ChecklistSubmitRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The checklist version the caller read")
        Long expectedVersion) {
}
