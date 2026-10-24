package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/** The lead's latest activity time read back after a checklist History entry, and that entry. */
public record ChecklistActivityResult(Instant lastActivityAt, ChecklistHistoryEntry entry) {
}
