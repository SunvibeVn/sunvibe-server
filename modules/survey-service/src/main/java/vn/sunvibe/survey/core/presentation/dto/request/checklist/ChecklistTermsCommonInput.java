package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** The common G8 terms, as sent (survey-checklist spec 4.6). */
@Schema(description = "The common G8 terms, as sent")
public record ChecklistTermsCommonInput(
        @JsonProperty(required = true)
        ChecklistChargeTermInput electricity,
        @JsonProperty(required = true)
        ChecklistChargeTermInput water,
        @JsonProperty(required = true)
        ChecklistChargeTermInput internet,
        @JsonProperty(required = true)
        ChecklistChargeTermInput parking,
        @JsonProperty(required = true)
        ChecklistChargeTermInput serviceFee,
        @JsonProperty(required = true)
        ChecklistFeeListTermInput otherFees,
        @JsonProperty(required = true)
        ChecklistTextTermInput houseRules,
        @JsonProperty(required = true)
        ChecklistPetsTermInput pets,
        @JsonProperty(required = true)
        ChecklistTextTermInput handoverMaintenance) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTermsCommonInput[hidden]";
    }
}
