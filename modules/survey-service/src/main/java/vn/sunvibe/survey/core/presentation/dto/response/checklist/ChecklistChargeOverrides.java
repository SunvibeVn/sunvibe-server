package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The charges that differ for one space, each null for the common charge (survey-checklist spec 4.6). */
@Schema(description = "The charges that differ for one space, each null for the common charge")
public record ChecklistChargeOverrides(
        @Schema(nullable = true)
        ChecklistChargeTerm electricity,
        @Schema(nullable = true)
        ChecklistChargeTerm water,
        @Schema(nullable = true)
        ChecklistChargeTerm internet,
        @Schema(nullable = true)
        ChecklistChargeTerm parking,
        @Schema(nullable = true)
        ChecklistChargeTerm serviceFee) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistChargeOverrides[hidden]";
    }
}
