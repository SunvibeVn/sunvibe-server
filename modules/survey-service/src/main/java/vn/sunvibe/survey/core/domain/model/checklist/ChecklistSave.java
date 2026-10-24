package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.Instant;

/**
 * One row of the insert-only save log (survey-checklist spec 5.2, I-6): which group reached which version, by whom and
 * when, and, on the row of the group the request named, the correction. Content is never logged here.
 */
public record ChecklistSave(long id, SurveyChecklistId checklistId, ChecklistGroup group, long groupVersion,
                            String actorUserId, Instant savedAt, Correction correctionOrNull) {
}
