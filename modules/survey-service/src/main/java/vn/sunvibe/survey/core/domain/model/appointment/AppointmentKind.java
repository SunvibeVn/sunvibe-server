package vn.sunvibe.survey.core.domain.model.appointment;

/** Whether a booking is the first survey of the intent cycle or a re-survey (survey-schedule spec 4.5). */
public enum AppointmentKind {
    /** No visit in the lead's current intent cycle yet. */
    FIRST,
    /**
     * The current intent cycle already has a visit; the booking links it and gives a reason (survey-schedule US-3
     * AC-2).
     */
    RESURVEY
}
