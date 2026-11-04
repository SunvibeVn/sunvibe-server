package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewReason;

import java.time.Instant;

/** One row of the review queue (survey-checklist submission spec 7.1). */
@Schema(description = "A revision waiting for a decision")
public record ChecklistReviewQueueItemResponse(
        String checklistId,
        int checklistNo,
        String leadId,
        String leadCode,
        Instant submittedAt,
        ChecklistStaffMember submittedBy,
        boolean canReview,
        @Schema(nullable = true)
        ReviewReason reason) {
}
