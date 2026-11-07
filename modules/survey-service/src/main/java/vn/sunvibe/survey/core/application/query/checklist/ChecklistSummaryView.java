package vn.sunvibe.survey.core.application.query.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.policy.checklist.Editability;

import java.time.Instant;

/**
 * One checklist of a VISITED appointment in the survey block (survey-checklist spec 7.2 {@code checklists[]}): editable
 * for this viewer or why not, the number of missing items, the holder and the latest group save (else the creation);
 * the decision's outcome once there is one (submission spec 7.2); whether a PASSED was withdrawn from use
 * (property-create requalification spec 7), never why.
 */
public record ChecklistSummaryView(String id, int checklistNo, ChecklistStatus status, DecisionOutcome outcomeOrNull,
                                   Editability editability,
                                   int missingCount, String holderUserId, Instant updatedAt,
                                   String updatedByUserIdOrNull, boolean requalified) {
}
