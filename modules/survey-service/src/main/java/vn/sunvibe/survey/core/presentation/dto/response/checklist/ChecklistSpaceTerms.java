package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The effective G8 values of one space in the preview (survey-checklist spec 4.7). */
@Schema(description = "The effective G8 values of one space in the preview")
public record ChecklistSpaceTerms(
        String spaceId,
        ChecklistEffectiveCharge electricity,
        ChecklistEffectiveCharge water,
        ChecklistEffectiveCharge internet,
        ChecklistEffectiveCharge parking,
        ChecklistEffectiveCharge serviceFee,
        ChecklistEffectiveFees fees) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSpaceTerms[" + spaceId + "]";
    }
}
