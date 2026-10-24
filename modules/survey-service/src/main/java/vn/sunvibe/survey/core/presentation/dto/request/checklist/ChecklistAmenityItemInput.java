package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistAmenitySource;

import java.time.LocalDate;

/** A checked amenity of a level, as sent (survey-checklist spec 4.6 G6). */
@Schema(description = "A checked amenity of a level, as sent")
public record ChecklistAmenityItemInput(
        @JsonProperty(required = true)
        AmenityCode code,
        @JsonProperty(required = true)
        YesNo presence,
        @JsonProperty(required = true)
        ChecklistAmenitySource source,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_STATED only")
        LocalDate confirmedOn,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_STATED only")
        String receivedByUserId,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_STATED only, exactly when someone else heard it")
        String relayBasis) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAmenityItemInput[" + code + "]";
    }
}
