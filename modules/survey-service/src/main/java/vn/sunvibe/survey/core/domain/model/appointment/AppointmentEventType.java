package vn.sunvibe.survey.core.domain.model.appointment;

/** What happened to a survey appointment (survey-schedule spec 5.2, survey_appointment_events.type). */
public enum AppointmentEventType {
    /** The appointment was booked; the first holder starts here. */
    BOOKED,
    /** Its editable values changed (a new revision). */
    CHANGED,
    /** Staff recorded the owner's confirmation. */
    CONFIRMED,
    /** The owner's confirmation was removed by staff. */
    UNCONFIRMED,
    /** The lead was reassigned and the appointment followed (a new holder starts here). */
    SURVEYOR_CHANGED,
    /** The appointment was cancelled by staff or by a lead command. */
    CANCELLED,
    /** A visit was recorded. */
    VISITED
}
