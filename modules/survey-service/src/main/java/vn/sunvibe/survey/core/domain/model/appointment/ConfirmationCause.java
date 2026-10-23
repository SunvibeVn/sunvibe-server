package vn.sunvibe.survey.core.domain.model.appointment;

/** Why an appointment is unconfirmed (survey-schedule US-1 AC-5, spec A-28); a later cause replaces an earlier one. */
public enum ConfirmationCause {
    /** Booked without the owner's confirmation. */
    NEW,
    /** The start or the duration changed (also when the place changed with it). */
    TIME_CHANGED,
    /** Only the meeting place changed. */
    LOCATION_CHANGED,
    /** The lead was reassigned and the appointment got a new surveyor. */
    REASSIGNED,
    /** Staff removed the confirmation. */
    USER
}
