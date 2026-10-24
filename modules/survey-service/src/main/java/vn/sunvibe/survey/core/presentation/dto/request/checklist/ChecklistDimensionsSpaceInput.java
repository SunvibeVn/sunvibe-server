package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** G3 values of one space, as sent (survey-checklist spec 4.6). */
@Schema(description = "G3 values of one space, as sent")
public record ChecklistDimensionsSpaceInput(
        @JsonProperty(required = true)
        String spaceId,
        @JsonProperty(required = true)
        ChecklistAreaFieldInput area,
        @JsonProperty(required = true)
        ChecklistCountFieldInput bedrooms,
        @JsonProperty(required = true)
        ChecklistCountFieldInput privateBathrooms,
        @JsonProperty(required = true)
        ChecklistYesNoCountFieldInput sharedBathroomUse,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..1000 characters")
        String layoutNote) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDimensionsSpaceInput[" + spaceId + "]";
    }
}
