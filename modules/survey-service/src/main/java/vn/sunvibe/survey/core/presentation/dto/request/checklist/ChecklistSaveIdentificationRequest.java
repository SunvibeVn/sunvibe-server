package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Save G1 (survey-checklist spec 7.1); otherGroupVersions names each group the save cascades into. */
@Schema(description = "Save G1")
public record ChecklistSaveIdentificationRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The G1 version the caller read")
        Long expectedVersion,
        @Schema(nullable = true, description = "Required for each group a space removal or scope change clears")
        ChecklistOtherGroupVersions otherGroupVersions,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Required when the save changes what someone else observed or heard")
        ChecklistCorrectionRequest correction,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        ChecklistIdentificationInput content) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSaveIdentificationRequest[" + expectedVersion + "]";
    }
}
