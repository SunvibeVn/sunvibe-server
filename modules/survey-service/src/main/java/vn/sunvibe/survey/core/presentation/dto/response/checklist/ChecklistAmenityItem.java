package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.AmenityCode;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;

import java.time.LocalDate;

/** A checked amenity of a level, as read (survey-checklist spec 4.6 G6). */
@Schema(description = "A checked amenity of a level, as read")
public record ChecklistAmenityItem(
        AmenityCode code,
        YesNo presence,
        ChecklistAmenitySource source,
        @Schema(nullable = true)
        LocalDate confirmedOn,
        @Schema(nullable = true)
        String receivedByUserId,
        @Schema(nullable = true)
        String relayBasis,
        @Schema(nullable = true)
        ChecklistUnitMeta meta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistAmenityItem[" + code + "]";
    }
}
