package vn.sunvibe.survey.core.domain.model.checklist;

/** What a return asks for (survey-checklist submission spec 4.5, US-3 AC-2, AC-2a). */
public enum NeedsChangesKind {
    /** More documents: a new revision on the same visit (survey-checklist D3-4). */
    DOCUMENTS_ONLY,
    /** A new visit is needed: the lead goes back to CONTACTED. */
    RESURVEY_REQUIRED
}
