package vn.sunvibe.survey.core.domain.model.checklist;

/** Whether a space is lived in, as seen on the arrival date (survey-checklist spec 4.6, BR-4). */
public enum Occupancy {
    /** Nobody lives there. */
    VACANT,
    /** Someone lives there. */
    OCCUPIED,
    /** Works are going on. */
    UNDER_RENOVATION,
    /** Not offered for now. */
    SUSPENDED
}
