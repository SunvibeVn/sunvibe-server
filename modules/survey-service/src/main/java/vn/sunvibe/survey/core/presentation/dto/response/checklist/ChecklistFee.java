package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChargeUnit;

/** One fee of a fee list (survey-checklist BR-5). */
@Schema(description = "One fee of a fee list")
public record ChecklistFee(
        @JsonProperty(required = true)
        @Schema(description = "1..100 characters")
        String name,
        @JsonProperty(required = true)
        ChecklistMoney amount,
        @JsonProperty(required = true)
        ChargeUnit chargeUnit) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistFee[" + chargeUnit + "]";
    }
}
