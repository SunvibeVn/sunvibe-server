package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A frozen photo the reviewer does not accept (survey-checklist submission spec A-5). */
@Schema(description = "A frozen photo the reviewer does not accept")
public record ChecklistRejectedEvidenceRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true)
        String mediaId,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, no phone number")
        String reason) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "ChecklistRejectedEvidenceRequest[" + mediaId + "]";
    }
}
