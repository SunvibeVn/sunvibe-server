package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What the owner's own web submission holds for the values a survey checklist may mark as "owner's form"
 * (survey-checklist BR-21, spec A-18, A-27): when it was sent, the address detail, province, ward and pin and the
 * available-from date as the lead keeps them (no command changes them), and the asking price per month as submitted
 * (null when the submission had no price or a price per another unit). Private location data: never logged.
 */
public record SurveyLeadOwnerForm(Instant submittedAt, String addressDetail, String provinceCode, String wardCode,
                                  BigDecimal latitude, BigDecimal longitude, Long monthlyAskingPriceOrNull,
                                  LocalDate availableFromOrNull) {

    /** Location data: never print it. */
    @Override
    public String toString() {
        return "SurveyLeadOwnerForm[hidden]";
    }
}
