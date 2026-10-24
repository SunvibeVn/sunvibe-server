package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The owner's own web form values an owner's-form source may use (survey-checklist spec A-27). */
@Schema(description = "The owner's own web form values an owner's-form source may use")
public record ChecklistOwnerForm(
        LocalDate submittedOn,
        String addressDetail,
        String provinceCode,
        String wardCode,
        @Schema(nullable = true)
        BigDecimal latitude,
        @Schema(nullable = true)
        BigDecimal longitude,
        @Schema(nullable = true, description = "Null unless a price per month was submitted")
        ChecklistRentMoney monthlyAskingPrice,
        @Schema(nullable = true)
        LocalDate availableFrom) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistOwnerForm[hidden]";
    }
}
