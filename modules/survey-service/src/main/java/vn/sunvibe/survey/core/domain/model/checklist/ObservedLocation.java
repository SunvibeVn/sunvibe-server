package vn.sunvibe.survey.core.domain.model.checklist;

import java.math.BigDecimal;

/**
 * The address seen or given at the survey (survey-checklist US-1 AC-2, BR-19): address text, province and ward of the
 * catalog and an optional pin, checked by the intake location rules. Private location data: never logged.
 */
public record ObservedLocation(String addressText, String provinceCode, String wardCode, BigDecimal latitude,
                               BigDecimal longitude) {

    @Override
    public String toString() {
        return "ObservedLocation[hidden]";
    }
}
