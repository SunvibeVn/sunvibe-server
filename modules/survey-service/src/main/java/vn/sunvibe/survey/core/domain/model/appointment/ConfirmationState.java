package vn.sunvibe.survey.core.domain.model.appointment;

/** Whether staff recorded that the owner agreed to the appointment (survey-schedule US-1 AC-3, AC-5). */
public enum ConfirmationState {
    /** Staff ticked "owner confirmed" for the current time and place. */
    CONFIRMED,
    /** Not confirmed by the owner (yet, or any more); never shown as agreed. */
    UNCONFIRMED
}
