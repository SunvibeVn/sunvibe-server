package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

/**
 * Resume the survey work of a held lead from its current visit (survey-schedule BR-11, B-6): a reason and the
 * owner's confirmation that the requirement and scope have not changed.
 */
public record ResumeSurveyCommand(String leadId, long expectedLeadVersion, String reason, Basis basis) {

    public ResumeSurveyCommand {
        reason = SurveyText.require("reason", reason, SurveyAppointment.MAX_REASON_LENGTH);
        if (basis == null) {
            throw new InvalidSurveyValueException("basis", "basis is required.");
        }
    }

    @Override
    public String toString() {
        return "ResumeSurveyCommand[" + leadId + "]";
    }
}
