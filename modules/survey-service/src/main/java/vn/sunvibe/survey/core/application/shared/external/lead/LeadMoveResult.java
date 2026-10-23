package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/** The lead after a survey move: status, new version, latest activity time and the History entry written. */
public record LeadMoveResult(String status, long version, Instant lastActivityAt, LeadHistoryEntry entry) {
}
