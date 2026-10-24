package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/**
 * One checklist event for the lead History: the actor, the checklist and its number and, only for a save, the group
 * enum name as the detail. Never text (survey-checklist BR-15, survey BR-14).
 */
public record ChecklistActivity(ChecklistActivityType type, String actorUserId, String checklistId, int checklistNo,
                                String detailOrNull, Instant occurredAt) {
}
