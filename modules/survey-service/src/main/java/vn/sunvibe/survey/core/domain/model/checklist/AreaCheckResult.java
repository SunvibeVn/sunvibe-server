package vn.sunvibe.survey.core.domain.model.checklist;

/** The on-site check of a required area (survey-checklist spec 4.6, BR-17). */
public enum AreaCheckResult {
    /** The whole area was entered and looked at. */
    INSPECTED,
    /** Not entered, or only partly entered; it stays missing. */
    NOT_ENTERED
}
