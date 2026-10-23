package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.math.BigDecimal;

/**
 * The lead's own place as the owner gave it (address, province and ward with their names, optional pin). Private
 * location data: never logged.
 */
public record SurveyLeadPlace(String addressDetail, String provinceCode, String provinceName, String wardCode,
                              String wardName, BigDecimal latitude, BigDecimal longitude) {

    /** Location data: never print it. */
    @Override
    public String toString() {
        return "SurveyLeadPlace[hidden]";
    }
}
