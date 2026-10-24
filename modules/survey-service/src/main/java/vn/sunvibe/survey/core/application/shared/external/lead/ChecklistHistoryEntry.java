package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/** The lead History entry a checklist command wrote: the checklist reference and, for a save, the group; no text. */
public record ChecklistHistoryEntry(String type, String actorUserId, String actorName, String referenceId,
                                    Integer referenceNo, String referenceDetail, Instant occurredAt) {
}
