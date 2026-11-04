package vn.sunvibe.survey.core.application.shared.external.lead;

/** The lead status a survey command asks for (survey-schedule spec 4.3). */
public enum LeadMoveTarget {
    /** The lead keeps its status (its version still goes up). */
    KEEP,
    /** The lead moves to SURVEY_SCHEDULED. */
    SURVEY_SCHEDULED,
    /** The lead goes back to CONTACTED. */
    CONTACTED,
    /** The lead becomes QUALIFIED: a PASSED decision or the resume of a usable PASSED. */
    QUALIFIED,
    /** The lead closes as REJECTED: a FAILED decision out of scope or not accepted. */
    REJECTED
}
