package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.YesNo;

import java.time.LocalDate;

/** An amenity outside the v1 codes, as read (survey-checklist spec 4.6 G6). */
@Schema(description = "An amenity outside the v1 codes, as read")
public record ChecklistOtherItem(
        String itemId,
        String label,
        @Schema(nullable = true)
        String spaceId,
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
        return "ChecklistOtherItem[" + itemId + "]";
    }
}
