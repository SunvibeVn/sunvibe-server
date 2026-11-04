package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

import java.util.List;

/**
 * DTO - ChecklistDecisionRequest (Record)
 * <p>
 * Decide a submitted revision (survey-checklist submission spec 4.5, 7.1, A-5, A-10).
 */
@Schema(description = "Decide a submitted survey checklist revision")
public record ChecklistDecisionRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The checklist version the caller read")
        Long expectedVersion,
        @JsonProperty(required = true)
        @Schema(nullable = true)
        DecisionOutcome outcome,
        @Schema(nullable = true, description = "Required with NEEDS_CHANGES, refused otherwise")
        NeedsChangesKind needsChangesKind,
        @Schema(nullable = true, description = "Required with FAILED, refused otherwise")
        FailedKind failedKind,
        @Schema(nullable = true, description = "1..2000 characters, no phone number; required with NEEDS_CHANGES and "
                + "FAILED")
        String reason,
        @Schema(nullable = true, description = "1..2000 characters, no phone number")
        String notes,
        @Schema(nullable = true, description = "1..2000 characters, no phone number; only with PASSED")
        String limitations,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "The frozen photos the reviewer does not accept; every other one counts "
                + "as accepted")
        List<ChecklistRejectedEvidenceRequest> rejectedEvidence) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "ChecklistDecisionRequest[" + outcome + "]";
    }
}
