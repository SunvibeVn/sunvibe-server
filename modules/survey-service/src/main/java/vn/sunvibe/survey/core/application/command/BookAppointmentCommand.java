package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Instant;

/**
 * Book a survey for a lead (survey-schedule US-1, US-3 AC-2; spec 7.1). The values are checked and cleaned here, before
 * the lead is looked up (spec 5.5: body first): a whole-minute start, a duration of 15..480 in steps of 15 (60 when
 * left out, survey-schedule spec A-5), the free texts by their limits without phone numbers. A missing meeting place
 * means the lead's own place.
 */
public record BookAppointmentCommand(String leadId, long expectedLeadVersion, Instant startAt,
                                     Integer durationMinutes, MeetingPlaceInput meetingLocation,
                                     String locationReason, String accessNote, String onSiteContactName,
                                     String onSiteContactRole, boolean ownerConfirmed, String confirmationNote,
                                     String resurveyReason) {

    public BookAppointmentCommand {
        SurveyTimeRules.requireWholeMinute("startAt", startAt);
        durationMinutes = durationMinutes == null ? SurveyTimeRules.DEFAULT_DURATION : durationMinutes;
        SurveyTimeRules.requireDuration(durationMinutes);
        locationReason = SurveyText.optional("locationReason", locationReason, SurveyAppointment.MAX_REASON_LENGTH);
        accessNote = SurveyText.optional("accessNote", accessNote, AppointmentDetails.MAX_ACCESS_NOTE_LENGTH);
        onSiteContactName = SurveyText.optional("onSiteContactName", onSiteContactName,
                AppointmentDetails.MAX_CONTACT_LENGTH);
        onSiteContactRole = SurveyText.optional("onSiteContactRole", onSiteContactRole,
                AppointmentDetails.MAX_CONTACT_LENGTH);
        confirmationNote = SurveyText.optional("confirmationNote", confirmationNote,
                SurveyAppointment.MAX_CONFIRMATION_NOTE_LENGTH);
        resurveyReason = SurveyText.optional("resurveyReason", resurveyReason, SurveyAppointment.MAX_REASON_LENGTH);
    }

    @Override
    public String toString() {
        return "BookAppointmentCommand[" + leadId + "]";
    }
}
