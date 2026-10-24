package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistFit;

/**
 * Fit field of a survey checklist, as sent (survey-checklist spec 7.2). Every key is sent (survey-checklist spec A-4,
 * plan PD-2).
 */
@Schema(description = "Fit field of a survey checklist, as sent")
public record ChecklistFitFieldInput(
        @JsonProperty(required = true)
        FieldState state,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Present exactly when state is VALUE")
        ChecklistFit value,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Present exactly when state is NOT_APPLICABLE, 1..500 characters")
        String naReason) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistFitFieldInput[" + state + "]";
    }
}
