package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewReason;

import java.util.List;

/** Whether the viewer can decide the revision (survey-checklist submission spec 4.2, 7.2). */
@Schema(description = "Whether the viewer can decide the revision now")
public record ChecklistReviewResponse(
        boolean canReview,
        @Schema(nullable = true, description = "The first reason, in the order of the decision command")
        ReviewReason reason,
        @Schema(description = "The errors[].field of every PASSED rule that fails now: hazards, evidence.unavailable, "
                + "appointment, evidence.coverage. Empty unless the revision is SUBMITTED")
        List<String> decisionBlockedBy) {
}
