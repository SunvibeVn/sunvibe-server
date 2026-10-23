package vn.sunvibe.survey.core.domain.model.resumption;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.time.Instant;

/**
 * A held lead whose survey work goes on from its current visit (survey-schedule BR-11, B-6): the visit it resumes,
 * why, on what basis, who and when. Insert-only (survey BR-5). The id is a Snowflake number given by the caller.
 * Reason and basis are staff text: never logged.
 */
public record SurveyResumption(long id, String leadId, SurveyAppointmentId visitAppointmentId, String reason,
                               Basis basis, String actorUserId, Instant occurredAt) {

    public SurveyResumption {
        if (leadId == null || visitAppointmentId == null || reason == null || basis == null || actorUserId == null
                || occurredAt == null) {
            throw new IllegalArgumentException("A resumption needs its lead, visit, reason, basis, actor and time.");
        }
    }

    /** A resumption; reason and basis are the checked, cleaned values. */
    public static SurveyResumption create(long id, String leadId, SurveyAppointmentId visitAppointmentId,
                                          String reason, Basis basis, String actorUserId, Instant occurredAt) {
        return new SurveyResumption(id, leadId, visitAppointmentId, reason, basis, actorUserId, occurredAt);
    }

    @Override
    public String toString() {
        return "SurveyResumption[" + id + "]";
    }
}
