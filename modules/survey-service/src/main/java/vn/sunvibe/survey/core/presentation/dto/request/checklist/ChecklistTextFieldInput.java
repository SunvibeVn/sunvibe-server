package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;

/**
 * Text field of a survey checklist, as sent (survey-checklist spec 7.2). Every key is sent (survey-checklist spec A-4,
 * plan PD-2).
 */
@Schema(description = "Text field of a survey checklist, as sent")
public record ChecklistTextFieldInput(
        @JsonProperty(required = true)
        FieldState state,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Present exactly when state is VALUE")
        String value,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Present exactly when state is NOT_APPLICABLE, 1..500 characters")
        String naReason) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTextFieldInput[" + state + "]";
    }
}
