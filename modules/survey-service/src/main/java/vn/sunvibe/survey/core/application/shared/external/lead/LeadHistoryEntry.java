package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/**
 * The lead History entry a survey command wrote: no text, only the appointment reference (survey-schedule BR-8).
 * Statuses are null when the lead kept its status.
 */
public record LeadHistoryEntry(String type, String actorUserId, String actorName, String fromStatus,
                               String toStatus, String referenceId, Integer referenceNo, Instant occurredAt) {
}
