package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.DepositBasis;

/** A deposit as months (0..12) or an amount; 0 means no deposit (survey-checklist BR-5). */
@Schema(description = "A deposit as months (0..12) or an amount; 0 means no deposit")
public record ChecklistDeposit(
        @JsonProperty(required = true)
        DepositBasis basis,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "MONTHS only, 0..12")
        Integer months,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "AMOUNT only")
        ChecklistMoney amount) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDeposit[" + basis + "]";
    }
}
