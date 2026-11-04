package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

import java.time.Instant;

/** The decision on a revision (survey-checklist submission spec 7.2). */
@Schema(description = "The decision on the revision")
public record ChecklistDecisionResponse(
        DecisionOutcome outcome,
        @Schema(nullable = true)
        NeedsChangesKind needsChangesKind,
        @Schema(nullable = true)
        FailedKind failedKind,
        @Schema(nullable = true)
        String reason,
        @Schema(nullable = true)
        String notes,
        @Schema(nullable = true)
        String limitations,
        ChecklistStaffMember reviewer,
        Instant decidedAt) {

    /** The reviewer's text is private: never print it. */
    @Override
    public String toString() {
        return "ChecklistDecisionResponse[" + outcome + "]";
    }
}
