package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

/**
 * Cancel a scheduled appointment with one of the five user reasons (survey-schedule US-2 AC-2, spec A-26): a
 * description of 1..500 characters is required for OTHER and optional otherwise.
 */
public record CancelAppointmentCommand(String appointmentId, long expectedLeadVersion,
                                       long expectedAppointmentVersion, CancelReason cancelReason,
                                       String description) {

    public CancelAppointmentCommand {
        if (cancelReason == null || !cancelReason.isUserChoice()) {
            throw new InvalidSurveyValueException("cancelReason",
                    "cancelReason must be one of the reasons staff may pick.");
        }
        description = SurveyText.optional("description", description,
                SurveyAppointment.MAX_USER_DESCRIPTION_LENGTH);
        if (cancelReason == CancelReason.OTHER && description == null) {
            throw new InvalidSurveyValueException("description", "description is required for the reason OTHER.");
        }
    }

    @Override
    public String toString() {
        return "CancelAppointmentCommand[" + appointmentId + ", " + cancelReason + "]";
    }
}
