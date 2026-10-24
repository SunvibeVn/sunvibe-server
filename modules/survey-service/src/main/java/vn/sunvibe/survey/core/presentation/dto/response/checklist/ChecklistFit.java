package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.Fit;

/** The fit judgement of the six-type rental scope with its reason (survey-checklist spec 4.6). */
@Schema(description = "The fit judgement of the six-type rental scope with its reason")
public record ChecklistFit(
        @JsonProperty(required = true)
        Fit assessment,
        @JsonProperty(required = true)
        @Schema(description = "1..1000 characters")
        String reason) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistFit[" + assessment + "]";
    }
}
