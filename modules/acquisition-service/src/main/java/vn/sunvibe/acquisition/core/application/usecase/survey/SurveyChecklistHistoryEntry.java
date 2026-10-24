package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * The lead History entry a survey checklist command wrote, in the shape of the lead queue's activity response: the
 * checklist reference and, for a save, the group; never text (survey-checklist spec 7.2).
 */
public record SurveyChecklistHistoryEntry(String type, String actorUserId, String actorName, String referenceId,
                                          Integer referenceNo, String referenceDetail, Instant occurredAt) {
}
