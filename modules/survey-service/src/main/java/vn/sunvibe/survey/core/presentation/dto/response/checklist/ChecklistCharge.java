package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeBasis;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;

/** A charge: its basis and, exactly for AMOUNT, the amount and its unit (survey-checklist BR-5). */
@Schema(description = "A charge: its basis and, exactly for AMOUNT, the amount and its unit")
public record ChecklistCharge(
        @JsonProperty(required = true)
        ChargeBasis basis,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "AMOUNT only")
        ChecklistMoney amount,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "AMOUNT only")
        ChargeUnit chargeUnit,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..200 characters")
        String note) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistCharge[" + basis + "]";
    }
}
