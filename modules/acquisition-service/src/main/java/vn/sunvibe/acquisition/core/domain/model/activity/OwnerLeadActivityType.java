package vn.sunvibe.acquisition.core.domain.model.activity;

/**
 * Kinds of entries in the history of an owner lead.
 */
public enum OwnerLeadActivityType {
    /** The owner sent the intake form. */
    SUBMITTED,
    /** The system gave the lead to the staff member responsible for the ward. */
    AUTO_ASSIGNED,
    /** A manager gave the lead to a staff member. */
    ASSIGNED,
    /** A manager moved the lead from one staff member to another. */
    REASSIGNED,
    /** The lead moved to another status. */
    STATUS_CHANGED,
    /** Staff added a note without changing the lead. */
    NOTE_ADDED,
    /** Staff booked a survey appointment (first survey or re-survey) for the lead (survey-schedule US-1, US-3). */
    SURVEY_BOOKED,
    /**
     * Staff changed the time, place, access note or on-site contact of the scheduled appointment (survey-schedule
     * US-2 AC-1).
     */
    SURVEY_CHANGED,
    /** Staff recorded that the owner confirmed the scheduled appointment (survey-schedule US-1 AC-5). */
    SURVEY_CONFIRMED,
    /** Staff removed the owner's confirmation of the scheduled appointment (survey-schedule US-1 AC-5). */
    SURVEY_UNCONFIRMED,
    /** Staff cancelled the scheduled appointment with a reason (survey-schedule US-2 AC-2). */
    SURVEY_CANCELLED,
    /** Staff recorded that someone reached the property for the appointment (survey-schedule US-3 AC-1). */
    SURVEY_VISITED,
    /** Staff resumed the survey work of a held lead from its current visit (survey-schedule BR-11). */
    SURVEY_RESUMED,
    /** Staff recorded a new requirement (intent and price) agreed with the owner (survey-schedule US-5). */
    INTENT_CHANGED,
    /** The assignee started a survey checklist draft on the current visit (survey-checklist US-1 AC-1, BR-15). */
    CHECKLIST_STARTED,
    /** A new assignee took a survey checklist draft over from its holder (survey-checklist BR-14, BR-15). */
    CHECKLIST_TAKEN_OVER,
    /** The holder saved one group of a survey checklist draft (survey-checklist BR-15). */
    CHECKLIST_SAVED
}
