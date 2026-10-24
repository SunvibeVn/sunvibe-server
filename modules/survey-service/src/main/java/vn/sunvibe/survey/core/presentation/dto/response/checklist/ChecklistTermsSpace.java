package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** G8 values of one space, as read (survey-checklist spec 4.6). */
@Schema(description = "G8 values of one space, as read")
public record ChecklistTermsSpace(
        String spaceId,
        ChecklistRentTerm monthlyRent,
        ChecklistDepositTerm deposit,
        ChecklistMonthsTerm paymentPeriodMonths,
        ChecklistMonthsTerm minimumTermMonths,
        @Schema(nullable = true, description = "Separate parts only")
        ChecklistChargeOverrides chargeOverrides,
        @Schema(nullable = true, description = "Separate parts only; adds to the common fees")
        ChecklistFeeListTerm spaceFees) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTermsSpace[" + spaceId + "]";
    }
}
