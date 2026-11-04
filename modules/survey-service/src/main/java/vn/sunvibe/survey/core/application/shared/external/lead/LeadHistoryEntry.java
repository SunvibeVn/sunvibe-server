package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/**
 * The lead History entry a survey command wrote: no text, only the reference and, for a review, its outcome and kind
 * (survey-schedule BR-8; survey-checklist submission spec 4.9). Statuses are null when the lead kept its status.
 */
public record LeadHistoryEntry(String type, String actorUserId, String actorName, String fromStatus,
                               String toStatus, String referenceId, Integer referenceNo, String referenceDetail,
                               Instant occurredAt) {
}
