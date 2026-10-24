package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A checked result: nothing observed, or a note of what was (survey-checklist spec 4.6). */
@Schema(description = "A checked result: nothing observed, or a note of what was")
public record ChecklistObservation(
        @JsonProperty(required = true)
        Boolean noneObserved,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..1000 characters, required when noneObserved is false")
        String note) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistObservation[" + noneObserved + "]";
    }
}
