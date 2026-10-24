package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** An amount in whole VND that is not a price per period (survey-checklist spec D-1). */
@Schema(description = "An amount in whole VND that is not a price per period")
public record ChecklistMoney(
        @JsonProperty(required = true)
        @Schema(description = "0 to 999999999999999; 0 means confirmed free or no deposit")
        Long amount,
        @JsonProperty(required = true)
        @Schema(description = "VND")
        String currency) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistMoney[" + currency + "]";
    }
}
