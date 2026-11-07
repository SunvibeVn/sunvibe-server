package vn.sunvibe.survey.core.application.query.checklist;

import vn.sunvibe.survey.core.application.query.common.LeadState;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;

/** The answer of a requalification (property-create requalification spec 7): the revision, the lead and the History entry. */
public record ChecklistRequalifyResult(ChecklistView view, LeadState lead, LeadHistoryEntry historyEntry) {
}
