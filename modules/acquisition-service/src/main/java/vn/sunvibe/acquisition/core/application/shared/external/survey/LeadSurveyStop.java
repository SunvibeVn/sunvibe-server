package vn.sunvibe.acquisition.core.application.shared.external.survey;

/** Why a lead command stopped the survey work (survey-schedule BR-3). */
public enum LeadSurveyStop {
    /** The lead was put on hold. */
    LEAD_ON_HOLD,
    /** The lead was closed (duplicate, rejected or withdrawn). */
    LEAD_CLOSED
}
