package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** A place of a survey appointment: the meeting place or the lead's place copied at booking. Private data. */
@Schema(description = "A place: address, province and ward with names, optional pin")
public record SurveyLocationResponse(String addressText, String provinceCode, String provinceName, String wardCode,
                                     String wardName, BigDecimal latitude, BigDecimal longitude) {

    @Override
    public String toString() {
        return "SurveyLocationResponse[hidden]";
    }
}
