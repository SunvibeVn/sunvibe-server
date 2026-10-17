package vn.sunvibe.acquisition.core.application.query;

/**
 * Result of adding a note: the new history entry and the lead after the note (with its current version).
 */
public record LeadNoteResult(LeadActivityView activity, OwnerLeadSummary lead) {
}
