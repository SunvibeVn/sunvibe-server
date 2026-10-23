package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Instant;

/**
 * Change a scheduled appointment (survey-schedule US-2 AC-1, spec A-4): the full new value of every editable field and
 * a reason; optional texts may be null to clear them. Checked and cleaned here, before any lookup.
 */
public record ChangeAppointmentCommand(String appointmentId, long expectedLeadVersion,
                                       long expectedAppointmentVersion, String reason, Instant startAt,
                                       int durationMinutes, MeetingPlaceInput meetingLocation, String accessNote,
                                       String onSiteContactName, String onSiteContactRole) {

    public ChangeAppointmentCommand {
        reason = SurveyText.require("reason", reason, SurveyAppointment.MAX_REASON_LENGTH);
        SurveyTimeRules.requireWholeMinute("startAt", startAt);
        SurveyTimeRules.requireDuration(durationMinutes);
        if (meetingLocation == null) {
            throw new InvalidSurveyValueException("meetingLocation", "meetingLocation is required.");
        }
        accessNote = SurveyText.optional("accessNote", accessNote, AppointmentDetails.MAX_ACCESS_NOTE_LENGTH);
        onSiteContactName = SurveyText.optional("onSiteContactName", onSiteContactName,
                AppointmentDetails.MAX_CONTACT_LENGTH);
        onSiteContactRole = SurveyText.optional("onSiteContactRole", onSiteContactRole,
                AppointmentDetails.MAX_CONTACT_LENGTH);
    }

    @Override
    public String toString() {
        return "ChangeAppointmentCommand[" + appointmentId + "]";
    }
}
