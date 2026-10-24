package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G2 parts, as sent (survey-checklist spec 7.1). */
@Schema(description = "G2 parts, as sent")
public record ChecklistPartsInput(
        @JsonProperty(required = true)
        ChecklistTextFieldInput accessRoute,
        @JsonProperty(required = true)
        ChecklistTextFieldInput sharedAreas,
        @JsonProperty(required = true)
        ChecklistCountFieldInput totalFloors,
        @JsonProperty(required = true)
        List<ChecklistPartsSpaceInput> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistPartsInput[hidden]";
    }
}
