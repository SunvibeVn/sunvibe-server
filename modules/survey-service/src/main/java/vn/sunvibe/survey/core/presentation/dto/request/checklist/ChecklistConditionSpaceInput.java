package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** G4 values of one space, as sent (survey-checklist spec 4.6). */
@Schema(description = "G4 values of one space, as sent")
public record ChecklistConditionSpaceInput(
        @JsonProperty(required = true)
        String spaceId,
        @JsonProperty(required = true)
        ChecklistOccupancyFieldInput occupancy,
        @JsonProperty(required = true)
        ChecklistDateTermInput expectedVacantFrom,
        @JsonProperty(required = true)
        ChecklistDateTermInput availableFrom,
        @JsonProperty(required = true)
        ChecklistAreaCheckFieldInput mainSpace,
        @JsonProperty(required = true)
        ChecklistAreaCheckFieldInput kitchen,
        @JsonProperty(required = true)
        ChecklistAreaCheckFieldInput bathroom) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistConditionSpaceInput[" + spaceId + "]";
    }
}
