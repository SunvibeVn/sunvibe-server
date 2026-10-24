package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * One survey checklist event for the lead History: the actor, the checklist and its number within the lead and, only
 * for a save, the group (its enum name) as the detail. No text (survey-checklist BR-15, survey BR-14).
 */
public record SurveyChecklistActivity(SurveyChecklistEvent event, String actorUserId, String checklistId,
                                      int checklistNo, String detailOrNull, Instant occurredAt) {
}
