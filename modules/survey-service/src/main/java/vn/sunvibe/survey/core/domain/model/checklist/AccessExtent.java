package vn.sunvibe.survey.core.domain.model.checklist;

/** How much of a space was entered at the visit (survey-checklist spec 4.6, BR-17). */
public enum AccessExtent {
    /** The whole space was entered. */
    ENTERED_ALL,
    /** Only part of the space was entered; a note says what was not reached. */
    ENTERED_PART
}
