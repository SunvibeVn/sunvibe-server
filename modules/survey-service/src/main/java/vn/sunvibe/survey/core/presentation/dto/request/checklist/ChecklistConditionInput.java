package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G4 condition, as sent (survey-checklist spec 7.1). */
@Schema(description = "G4 condition, as sent")
public record ChecklistConditionInput(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Required in separate parts, null otherwise")
        ChecklistAreaCheckFieldInput sharedKitchen,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Required in separate parts, null otherwise")
        ChecklistAreaCheckFieldInput sharedBathroom,
        @JsonProperty(required = true)
        List<ChecklistConditionSpaceInput> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistConditionInput[hidden]";
    }
}
