package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;

import java.time.Instant;

/** One resumption of a lead's survey work (survey-schedule BR-11): the visit, why, on what basis, who and when. */
public record ResumptionView(String visitAppointmentId, int visitAppointmentNo, String reason, Basis basis,
                             String actorUserId, Instant occurredAt) {

    @Override
    public String toString() {
        return "ResumptionView[" + visitAppointmentId + "]";
    }
}
