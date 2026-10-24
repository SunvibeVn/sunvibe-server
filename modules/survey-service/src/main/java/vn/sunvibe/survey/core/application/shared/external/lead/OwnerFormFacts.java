package vn.sunvibe.survey.core.application.shared.external.lead;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What the owner's own web submission holds for the "owner's form" source of a checklist (survey-checklist BR-21,
 * spec A-18, A-27): when it was sent, the address detail, province, ward and pin, the asking price per month (null
 * when none was submitted per month) and the available-from date (null when none). Private location data: never
 * logged.
 */
public record OwnerFormFacts(Instant submittedAt, String addressDetail, String provinceCode, String wardCode,
                             BigDecimal latitude, BigDecimal longitude, Long monthlyAskingPriceOrNull,
                             LocalDate availableFromOrNull) {

    /** Location data: never print it. */
    @Override
    public String toString() {
        return "OwnerFormFacts[hidden]";
    }
}
