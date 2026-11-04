package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;

/** The answer of a new revision (survey-checklist submission spec 7.1): the revision and the History entry. */
public record ChecklistRevisionResult(ChecklistView view, ChecklistHistoryEntry historyEntry) {
}
