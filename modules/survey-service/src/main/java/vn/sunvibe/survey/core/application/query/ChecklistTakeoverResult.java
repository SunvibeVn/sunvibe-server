package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;

/** The answer of a takeover (survey-checklist spec 7.1): the checklist and the History entry. */
public record ChecklistTakeoverResult(ChecklistView view, ChecklistHistoryEntry historyEntry) {
}
