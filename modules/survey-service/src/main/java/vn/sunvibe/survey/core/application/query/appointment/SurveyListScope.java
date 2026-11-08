package vn.sunvibe.survey.core.application.query.appointment;

/** Which appointments the survey list shows (survey-schedule US-4, survey-schedule spec 7.1). */
public enum SurveyListScope {
    /** The caller's own appointments (they are the surveyor). */
    MINE,
    /** Every appointment, optionally of one surveyor; not for a SALE. */
    ALL
}
