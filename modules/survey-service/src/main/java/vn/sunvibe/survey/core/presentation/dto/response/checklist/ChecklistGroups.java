package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The seven stored groups of a checklist (survey-checklist spec 7.2). */
@Schema(description = "The seven stored groups of a checklist")
public record ChecklistGroups(
        ChecklistIdentificationGroup identification,
        ChecklistPartsGroup parts,
        ChecklistDimensionsGroup dimensions,
        ChecklistConditionGroup condition,
        ChecklistSafetyGroup safety,
        ChecklistAmenitiesGroup amenities,
        ChecklistTermsGroup terms) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistGroups[hidden]";
    }
}
