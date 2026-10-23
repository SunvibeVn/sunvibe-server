package vn.sunvibe.survey.core.application.shared.external.lead;

import java.math.BigDecimal;

/** The lead's own place (address, province and ward with names, optional pin). Private location data. */
public record LeadPlace(String addressDetail, String provinceCode, String provinceName, String wardCode,
                        String wardName, BigDecimal latitude, BigDecimal longitude) {

    @Override
    public String toString() {
        return "LeadPlace[hidden]";
    }
}
