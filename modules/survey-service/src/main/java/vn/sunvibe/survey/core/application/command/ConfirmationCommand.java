package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

/**
 * Record or remove the owner's confirmation of a scheduled appointment (survey-schedule US-1 AC-5), with an optional
 * note (1..500 characters, no phone number).
 */
public record ConfirmationCommand(String appointmentId, long expectedLeadVersion, long expectedAppointmentVersion,
                                  boolean confirmed, String note) {

    public ConfirmationCommand {
        note = SurveyText.optional("note", note, SurveyAppointment.MAX_CONFIRMATION_NOTE_LENGTH);
    }

    @Override
    public String toString() {
        return "ConfirmationCommand[" + appointmentId + ", " + confirmed + "]";
    }
}
