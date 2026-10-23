package vn.sunvibe.acquisition.core.application.shared.external.survey;

/** A survey appointment a lead command changed: its id and its number within the lead. */
public record SurveyAppointmentRef(String appointmentId, int appointmentNo) {
}
