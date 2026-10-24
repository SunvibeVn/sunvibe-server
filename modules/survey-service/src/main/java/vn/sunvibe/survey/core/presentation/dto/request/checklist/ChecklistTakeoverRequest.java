package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - ChecklistTakeoverRequest (Record)
 * <p>
 * Take a draft over from its holder (survey-checklist spec 4.5, 7.1): the checklist version the caller read, why and
 * on what basis.
 */
@Schema(description = "Take a survey checklist draft over from its holder")
public record ChecklistTakeoverRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The checklist version the caller read")
        Long expectedVersion,

        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, no phone number")
        String reason,

        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistBasisRequest basis) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "ChecklistTakeoverRequest[" + expectedVersion + "]";
    }
}
