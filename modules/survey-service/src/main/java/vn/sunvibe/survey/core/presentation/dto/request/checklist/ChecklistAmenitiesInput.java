package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G6 amenities, as sent (survey-checklist spec 7.1). */
@Schema(description = "G6 amenities, as sent")
public record ChecklistAmenitiesInput(
        @JsonProperty(required = true)
        List<ChecklistAmenityItemInput> sharedItems,
        @JsonProperty(required = true)
        List<ChecklistAmenitiesSpaceInput> spaces,
        @JsonProperty(required = true)
        List<ChecklistOtherItemInput> otherItems) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAmenitiesInput[hidden]";
    }
}
