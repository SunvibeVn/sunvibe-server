package vn.sunvibe.survey.core.application.query;

/** Why a survey cannot be booked for the lead now, in the order of the booking checks (survey-schedule spec 7.2). */
public enum BookingBlock {
    /** The lead already has a scheduled appointment (survey-schedule US-1 AC-4). */
    ALREADY_SCHEDULED,
    /** The lead is NEW: nobody holds it yet, so there is no surveyor (survey-schedule C-1). */
    NO_ASSIGNEE,
    /** The current visit's latest revision waits for a decision (survey-checklist submission spec 4.8). */
    CHECKLIST_SUBMITTED,
    /** The cycle has a current PASSED revision, usable or not (survey-checklist submission spec 4.8). */
    CHECKLIST_PASSED,
    /** The lead's status does not allow the booking kind (spec 4.5). */
    LEAD_STATUS,
    /** The lead is not a rental lead (survey-schedule C-1, US-5 AC-3). */
    INTENT,
    /** The assignee can no longer take leads (survey-schedule BR-6). */
    SURVEYOR_INELIGIBLE
}
