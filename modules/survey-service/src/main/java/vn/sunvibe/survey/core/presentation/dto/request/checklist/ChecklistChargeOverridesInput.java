package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** The charges that differ for one space, each null for the common charge, as sent (survey-checklist spec 4.6). */
@Schema(description = "The charges that differ for one space, each null for the common charge, as sent")
public record ChecklistChargeOverridesInput(
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistChargeTermInput electricity,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistChargeTermInput water,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistChargeTermInput internet,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistChargeTermInput parking,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistChargeTermInput serviceFee) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistChargeOverridesInput[hidden]";
    }
}
