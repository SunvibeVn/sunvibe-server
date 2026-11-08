package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * The lead History entry a survey command wrote, in the shape of the lead queue's activity response: no text, only
 * the reference and, for a review, its outcome and kind (survey-schedule BR-8, survey-schedule spec 7.2; survey-checklist submission
 * spec 4.9). Statuses are null when the lead kept its status.
 */
public record SurveyLeadHistoryEntry(String type, String actorUserId, String actorName, String fromStatus,
                                     String toStatus, String referenceId, Integer referenceNo, String referenceDetail,
                                     Instant occurredAt) {
}
