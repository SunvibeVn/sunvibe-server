package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;

/** The answer of a decision (survey-checklist submission spec 7.1): the revision, the lead and the History entry. */
public record ChecklistDecideResult(ChecklistView view, LeadState lead, LeadHistoryEntry historyEntry) {
}
