package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** G5 safety, as sent (survey-checklist spec 7.1). */
@Schema(description = "G5 safety, as sent")
public record ChecklistSafetyInput(
        @JsonProperty(required = true)
        ChecklistTextFieldInput egress,
        @JsonProperty(required = true)
        ChecklistObservationFieldInput accessRestrictions,
        @JsonProperty(required = true)
        @Schema(description = "At most 30 hazards")
        ChecklistHazardListFieldInput hazards) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSafetyInput[hidden]";
    }
}
