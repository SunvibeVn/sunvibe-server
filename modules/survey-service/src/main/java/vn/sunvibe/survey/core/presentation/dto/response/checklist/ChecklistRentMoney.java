package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A monthly rent in whole VND (survey-checklist spec D-1). */
@Schema(description = "A monthly rent in whole VND")
public record ChecklistRentMoney(
        @JsonProperty(required = true)
        @Schema(description = "1 to 999999999999999")
        Long amount,
        @JsonProperty(required = true)
        @Schema(description = "VND")
        String currency,
        @JsonProperty(required = true)
        @Schema(description = "MONTH")
        String unit) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistRentMoney[" + unit + "]";
    }
}
