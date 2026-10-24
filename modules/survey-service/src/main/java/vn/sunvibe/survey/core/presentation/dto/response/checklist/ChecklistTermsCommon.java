package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The common G8 terms, as read (survey-checklist spec 4.6). */
@Schema(description = "The common G8 terms, as read")
public record ChecklistTermsCommon(
        ChecklistChargeTerm electricity,
        ChecklistChargeTerm water,
        ChecklistChargeTerm internet,
        ChecklistChargeTerm parking,
        ChecklistChargeTerm serviceFee,
        ChecklistFeeListTerm otherFees,
        ChecklistTextTerm houseRules,
        ChecklistPetsTerm pets,
        ChecklistTextTerm handoverMaintenance) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTermsCommon[hidden]";
    }
}
