package vn.sunvibe.survey.core.domain.policy.checklist;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The owner's own web form as the checklist may use it (survey-checklist spec A-18, A-27): the Vietnam date it was
 * sent, the address detail, province, ward and pin, the monthly asking price (null unless one per month was sent) and
 * the available-from date (null when none). Private location data: never logged.
 */
public record SubmittedForm(LocalDate submittedOn, String addressDetail, String provinceCode, String wardCode,
                            BigDecimal latitude, BigDecimal longitude, Long monthlyAskingPrice,
                            LocalDate availableFrom) {

    @Override
    public String toString() {
        return "SubmittedForm[hidden]";
    }
}
