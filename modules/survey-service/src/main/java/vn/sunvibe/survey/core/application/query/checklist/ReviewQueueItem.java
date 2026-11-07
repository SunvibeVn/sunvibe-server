package vn.sunvibe.survey.core.application.query.checklist;

import vn.sunvibe.survey.core.domain.policy.checklist.ReviewReason;

import java.time.Instant;

/**
 * One row of the review queue (survey-checklist submission spec 7.1): which revision of which lead, who submitted it and
 * when, and whether the viewer can decide it. No text of the record or of a review.
 */
public record ReviewQueueItem(String checklistId, int checklistNo, String leadId, String leadCode, Instant submittedAt,
                              String submittedByUserId, String submittedByNameOrNull, boolean canReview,
                              ReviewReason reasonOrNull) {
}
