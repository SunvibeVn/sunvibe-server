package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;

import java.time.LocalDate;

/** A hazard of G5, as read (survey-checklist spec 4.6.1). */
@Schema(description = "A hazard of G5, as read")
public record ChecklistHazard(
        String hazardId,
        @Schema(nullable = true)
        String spaceId,
        @Schema(nullable = true)
        ChecklistHazardLink previous,
        String description,
        HazardSeverity severity,
        HazardStatus status,
        @Schema(nullable = true)
        String statusNote,
        @Schema(nullable = true)
        LocalDate ownerReportedOn,
        @Schema(nullable = true)
        String receivedByUserId,
        @Schema(nullable = true)
        String receivedByName,
        @Schema(nullable = true)
        String relayBasis,
        @Schema(nullable = true)
        ChecklistUnitMeta meta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistHazard[" + hazardId + "]";
    }
}
