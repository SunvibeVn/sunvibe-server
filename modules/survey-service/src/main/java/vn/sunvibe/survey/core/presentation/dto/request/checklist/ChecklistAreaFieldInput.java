package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.FieldState;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAreaSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Area field in square metres with one decimal of a survey checklist, as sent (survey-checklist spec 7.2). Every key is
 * sent (survey-checklist spec A-4, plan PD-2).
 */
@Schema(description = "Area field in square metres with one decimal of a survey checklist, as sent")
public record ChecklistAreaFieldInput(
        @JsonProperty(required = true)
        FieldState state,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Present exactly when state is VALUE")
        BigDecimal value,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Present exactly when state is NOT_APPLICABLE, 1..500 characters")
        String naReason,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Required with VALUE")
        ChecklistAreaSource source,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_STATED only: the day the owner said it")
        LocalDate confirmedOn,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_STATED only: who heard it, one of the lead's staff")
        String receivedByUserId,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_STATED only, exactly when someone else heard it: how it reached "
                + "the person entering it")
        String relayBasis) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAreaFieldInput[" + state + "]";
    }
}
