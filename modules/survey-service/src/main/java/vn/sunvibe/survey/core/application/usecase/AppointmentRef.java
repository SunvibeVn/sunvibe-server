package vn.sunvibe.survey.core.application.usecase;

/** A survey appointment a lead command changed: its id and its number within the lead. */
public record AppointmentRef(String appointmentId, int appointmentNo) {
}
