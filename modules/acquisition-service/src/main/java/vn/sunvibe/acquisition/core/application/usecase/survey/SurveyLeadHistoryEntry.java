package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * The lead History entry a survey command wrote, in the shape of the lead queue's activity response: no text, only
 * the appointment reference (survey-schedule BR-8, spec 7.2). Statuses are null when the lead kept its status.
 */
public record SurveyLeadHistoryEntry(String type, String actorUserId, String actorName, String fromStatus,
                                     String toStatus, String referenceId, Integer referenceNo, Instant occurredAt) {
}
