package vn.sunvibe.survey.core.domain.model.appointment;

import vn.sunvibe.survey.core.domain.policy.SurveyText;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

import java.time.Instant;

/**
 * The editable values of a survey appointment (survey-schedule US-2 AC-1): start, duration, meeting place, access note
 * and on-site contact (name and role only, never a phone). Every revision keeps a full copy. Text is staff text: never
 * logged.
 */
public record AppointmentDetails(Instant startAt, int durationMinutes, MeetingLocation meetingLocation,
                                 String accessNote, String onSiteContactName, String onSiteContactRole) {

    public static final int MAX_ACCESS_NOTE_LENGTH = 1000;
    public static final int MAX_CONTACT_LENGTH = 100;

    public AppointmentDetails {
        if (startAt == null || meetingLocation == null) {
            throw new IllegalArgumentException("startAt and meetingLocation are required.");
        }
    }

    /**
     * Checked values (survey-schedule BR-4, spec A-14, A-24, A-25): a whole-minute start, a duration of 15..480 in
     * steps of 15, optional access note (1..1000) and on-site name and role (1..100 each) without phone numbers.
     * Whether the start lies in the booking window is checked by the appointment, which knows when it changes.
     *
     * @throws vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException on the field
     */
    public static AppointmentDetails of(Instant startAt, int durationMinutes, MeetingLocation meetingLocation,
                                        String accessNote, String onSiteContactName, String onSiteContactRole) {
        SurveyTimeRules.requireWholeMinute("startAt", startAt);
        SurveyTimeRules.requireDuration(durationMinutes);
        if (meetingLocation == null) {
            throw new IllegalArgumentException("meetingLocation is required.");
        }
        return new AppointmentDetails(startAt, durationMinutes, meetingLocation,
                SurveyText.optional("accessNote", accessNote, MAX_ACCESS_NOTE_LENGTH),
                SurveyText.optional("onSiteContactName", onSiteContactName, MAX_CONTACT_LENGTH),
                SurveyText.optional("onSiteContactRole", onSiteContactRole, MAX_CONTACT_LENGTH));
    }

    /** True when the start or the duration differs. */
    public boolean timeDiffersFrom(AppointmentDetails other) {
        return !startAt.equals(other.startAt) || durationMinutes != other.durationMinutes;
    }

    @Override
    public String toString() {
        return "AppointmentDetails[hidden]";
    }
}
