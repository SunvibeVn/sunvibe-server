package vn.sunvibe.survey.core.domain.model.appointment;

import java.time.Instant;

/**
 * One event of an appointment's history (survey-schedule spec 5.2): its type, who and when, the revision in force,
 * the surveyors of a reassignment, the confirmation cause it set and the confirmation note. Insert-only (survey BR-5).
 * The holders of an appointment are its BOOKED and SURVEYOR_CHANGED events.
 */
public record AppointmentEvent(AppointmentEventType type, String actorUserId, Instant occurredAt, Integer revisionNo,
                               String fromSurveyorUserId, String toSurveyorUserId,
                               ConfirmationCause confirmationCause, String note) {

    public AppointmentEvent {
        if (type == null || occurredAt == null) {
            throw new IllegalArgumentException("An event needs its type and time.");
        }
    }

    @Override
    public String toString() {
        return "AppointmentEvent[" + type + "]";
    }
}
