package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;

/**
 * Whether the lead can get a booking now (survey-schedule spec 7.2): the kind and, for a re-survey, the visit it
 * follows; otherwise the first check that blocks it.
 */
public record BookingState(boolean allowed, AppointmentKind kind, String previousVisitId, BookingBlock blockedBy) {
}
