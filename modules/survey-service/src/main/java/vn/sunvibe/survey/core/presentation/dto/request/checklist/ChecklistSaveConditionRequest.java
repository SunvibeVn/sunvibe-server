package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Save the condition group (survey-checklist spec 7.1). */
@Schema(description = "Save the condition group")
public record ChecklistSaveConditionRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The group version the caller read")
        Long expectedVersion,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Required when the save changes what someone else observed or heard")
        ChecklistCorrectionRequest correction,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistConditionInput content) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSaveConditionRequest[" + expectedVersion + "]";
    }
}
