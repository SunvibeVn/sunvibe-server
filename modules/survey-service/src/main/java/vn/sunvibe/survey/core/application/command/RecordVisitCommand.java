package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Instant;

/**
 * Record that someone reached the property (survey-schedule US-3 AC-1, AC-1a; spec A-8): the whole-minute arrival,
 * a note of 1..2000 characters, who went and, for a proxy record, the reason and basis.
 */
public record RecordVisitCommand(String appointmentId, long expectedLeadVersion, long expectedAppointmentVersion,
                                 Instant arrivedAt, String note, String performedByUserId, ProxyRecord proxy) {

    public RecordVisitCommand {
        SurveyTimeRules.requireWholeMinute("arrivedAt", arrivedAt);
        note = SurveyText.require("note", note, SurveyAppointment.MAX_VISIT_NOTE_LENGTH);
        if (performedByUserId == null || performedByUserId.isBlank()) {
            throw new InvalidSurveyValueException("performedByUserId", "performedByUserId is required.");
        }
    }

    @Override
    public String toString() {
        return "RecordVisitCommand[" + appointmentId + "]";
    }
}
