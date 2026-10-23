package vn.sunvibe.survey.core.application.usecase;

/** Why a lead command stopped the survey work (survey-schedule BR-3). */
public enum LeadStopReason {
    /** The lead was put on hold. */
    LEAD_ON_HOLD,
    /** The lead was closed. */
    LEAD_CLOSED
}
