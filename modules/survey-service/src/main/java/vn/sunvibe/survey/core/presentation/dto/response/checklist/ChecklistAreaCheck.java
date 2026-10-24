package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.AreaCheckResult;

/** The check of a main space, kitchen, bathroom or shared wet area (survey-checklist spec 4.6). */
@Schema(description = "The check of a main space, kitchen, bathroom or shared wet area")
public record ChecklistAreaCheck(
        @JsonProperty(required = true)
        AreaCheckResult result,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "INSPECTED only")
        Boolean noneObserved,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..1000 characters; for NOT_ENTERED what was seen from outside or what "
                + "the owner said")
        String note,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "NOT_ENTERED only: what was not reached and why, 1..500 characters")
        String notEnteredReason) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAreaCheck[" + result + "]";
    }
}
