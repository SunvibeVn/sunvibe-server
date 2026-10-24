package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.ChargeOrigin;

/** The charge a space gets and where it comes from (survey-checklist spec 7.2). */
@Schema(description = "The charge a space gets and where it comes from")
public record ChecklistEffectiveCharge(
        ChecklistChargeTerm term,
        ChargeOrigin origin) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistEffectiveCharge[" + origin + "]";
    }
}
