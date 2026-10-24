package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO - ChecklistIntakeRequest (Record)
 * <p>
 * The intake declaration of an assignee who did not perform the visit (survey-checklist BR-14): why they start the
 * draft and where their information comes from.
 */
@Schema(description = "The intake declaration of a starter who did not perform the visit")
public record ChecklistIntakeRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, no phone number")
        String reason,

        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistBasisRequest basis) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "ChecklistIntakeRequest[hidden]";
    }
}
