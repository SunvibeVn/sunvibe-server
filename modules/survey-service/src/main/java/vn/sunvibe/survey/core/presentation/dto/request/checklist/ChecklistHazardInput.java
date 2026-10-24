package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistHazardLink;

import java.time.LocalDate;

/** A hazard of G5, as sent; a new hazard carries a client ULID (survey-checklist spec 4.6.1). */
@Schema(description = "A hazard of G5, as sent; a new hazard carries a client ULID")
public record ChecklistHazardInput(
        @JsonProperty(required = true)
        @Schema(description = "ULID")
        String hazardId,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "A space of this checklist, or null for the whole property")
        String spaceId,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The hazard of an earlier checklist of this lead and requirement it "
                + "continues")
        ChecklistHazardLink previous,
        @JsonProperty(required = true)
        @Schema(description = "1..500 characters")
        String description,
        @JsonProperty(required = true)
        HazardSeverity severity,
        @JsonProperty(required = true)
        HazardStatus status,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters")
        String statusNote,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_REPORTED_FIXED only: the day the owner reported the fix")
        LocalDate ownerReportedOn,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "OWNER_REPORTED_FIXED only: who heard it, one of the lead's staff")
        String receivedByUserId,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Exactly when someone else heard it: how it reached the person entering "
                + "it")
        String relayBasis) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistHazardInput[" + hazardId + "]";
    }
}
