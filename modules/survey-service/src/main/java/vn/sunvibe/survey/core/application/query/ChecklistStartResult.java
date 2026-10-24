package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;

/** The answer of a start (survey-checklist spec 7.1): the checklist, the lead as it is now and the History entry. */
public record ChecklistStartResult(ChecklistView view, LeadState lead, ChecklistHistoryEntry historyEntry) {
}
