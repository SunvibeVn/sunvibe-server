package vn.sunvibe.survey.core.application.shared.external.lead;

/** The lead status a survey command asks for (survey-schedule spec 4.3). */
public enum LeadMoveTarget {
    /** The lead keeps its status (its version still goes up). */
    KEEP,
    /** The lead moves to SURVEY_SCHEDULED. */
    SURVEY_SCHEDULED,
    /** The lead goes back to CONTACTED. */
    CONTACTED
}
