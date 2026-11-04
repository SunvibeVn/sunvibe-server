package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;

/**
 * The answer of a submit (survey-checklist submission spec 7.1): the revision and its History entry, null on a replay
 * (spec A-4), which writes nothing.
 */
public record ChecklistSubmitResult(ChecklistView view, ChecklistHistoryEntry historyEntryOrNull) {
}
