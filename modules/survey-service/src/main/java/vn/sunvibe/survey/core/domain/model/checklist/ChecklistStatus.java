package vn.sunvibe.survey.core.domain.model.checklist;

/** The status of a checklist revision (survey-checklist spec 4.2); this slice has drafts only. */
public enum ChecklistStatus {
    /** Being recorded; never submitted in this slice (spec C-1). */
    DRAFT
}
