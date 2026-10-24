package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** G3 dimensions, as sent (survey-checklist spec 7.1). */
@Schema(description = "G3 dimensions, as sent")
public record ChecklistDimensionsInput(
        @JsonProperty(required = true)
        List<ChecklistDimensionsSpaceInput> spaces) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDimensionsInput[hidden]";
    }
}
