package vn.sunvibe.survey.core.domain.model.appointment;

/** Where a survey appointment stands (survey-schedule spec 4.4); VISITED and CANCELLED are final. */
public enum AppointmentStatus {
    /** Booked and waiting for the visit; may still change, be confirmed or cancelled. */
    SCHEDULED,
    /** Someone reached the property for it (survey-schedule US-3 AC-1). Final. */
    VISITED,
    /** Cancelled by staff or by a lead command (survey-schedule US-2 AC-2, BR-3). Final. */
    CANCELLED
}
