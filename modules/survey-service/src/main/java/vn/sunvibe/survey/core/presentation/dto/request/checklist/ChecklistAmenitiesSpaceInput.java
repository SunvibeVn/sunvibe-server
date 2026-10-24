package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G6 values of one space, as sent (survey-checklist spec 4.6). */
@Schema(description = "G6 values of one space, as sent")
public record ChecklistAmenitiesSpaceInput(
        @JsonProperty(required = true)
        String spaceId,
        @JsonProperty(required = true)
        ChecklistFurnishingFieldInput furnishing,
        @JsonProperty(required = true)
        List<ChecklistAmenityItemInput> items) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAmenitiesSpaceInput[" + spaceId + "]";
    }
}
