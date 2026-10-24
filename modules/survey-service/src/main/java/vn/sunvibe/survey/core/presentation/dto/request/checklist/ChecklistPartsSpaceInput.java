package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** G2 values of one space, as sent (survey-checklist spec 4.6). */
@Schema(description = "G2 values of one space, as sent")
public record ChecklistPartsSpaceInput(
        @JsonProperty(required = true)
        String spaceId,
        @JsonProperty(required = true)
        ChecklistTextFieldInput floor,
        @JsonProperty(required = true)
        ChecklistTextFieldInput privateAreas,
        @JsonProperty(required = true)
        ChecklistActualAccessFieldInput actualAccess) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistPartsSpaceInput[" + spaceId + "]";
    }
}
