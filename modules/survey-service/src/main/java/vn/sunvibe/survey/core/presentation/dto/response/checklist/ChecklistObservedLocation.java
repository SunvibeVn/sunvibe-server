package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * An address seen or given at the survey: address text, province and ward codes of the catalog, optional pin (survey-
 * checklist spec 4.6).
 */
@Schema(description = "An address seen or given at the survey: address text, province and ward codes of the catalog, "
        + "optional pin")
public record ChecklistObservedLocation(
        @JsonProperty(required = true)
        String addressText,
        @JsonProperty(required = true)
        String provinceCode,
        @JsonProperty(required = true)
        String wardCode,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Give both latitude and longitude, or neither")
        BigDecimal latitude,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        BigDecimal longitude) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistObservedLocation[hidden]";
    }
}
