package vn.sunvibe.survey.core.domain.model.checklist;

/** How serious a hazard is, as the Sale classifies it (survey-checklist BR-18). */
public enum HazardSeverity {
    /** Important; warns until seen resolved. */
    IMPORTANT,
    /** Minor. */
    MINOR,
    /** Not sure yet; counts as important for warnings. */
    UNCLASSIFIED
}
