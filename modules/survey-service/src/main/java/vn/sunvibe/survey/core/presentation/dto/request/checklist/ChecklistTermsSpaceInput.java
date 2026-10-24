package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** G8 values of one space, as sent (survey-checklist spec 4.6). */
@Schema(description = "G8 values of one space, as sent")
public record ChecklistTermsSpaceInput(
        @JsonProperty(required = true)
        String spaceId,
        @JsonProperty(required = true)
        ChecklistRentTermInput monthlyRent,
        @JsonProperty(required = true)
        ChecklistDepositTermInput deposit,
        @JsonProperty(required = true)
        ChecklistMonthsTermInput paymentPeriodMonths,
        @JsonProperty(required = true)
        ChecklistMonthsTermInput minimumTermMonths,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Required in separate parts, null otherwise")
        ChecklistChargeOverridesInput chargeOverrides,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Separate parts only; adds to the common fees")
        ChecklistFeeListTermInput spaceFees) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTermsSpaceInput[" + spaceId + "]";
    }
}
