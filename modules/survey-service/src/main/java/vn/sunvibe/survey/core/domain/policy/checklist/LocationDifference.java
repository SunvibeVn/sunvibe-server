package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.policy.PlaceComparison;

/**
 * How the observed address differs from the lead's place and from the meeting place, by the survey-schedule A-16
 * comparison (survey-checklist spec 4.6, BR-19). Both are shown; only {@code fromLead} needs the difference note. No
 * observed address: no difference.
 */
public record LocationDifference(boolean fromLead, boolean fromMeetingPlace) {

    public static final LocationDifference NONE = new LocationDifference(false, false);

    public static LocationDifference of(ObservedLocation observed, LeadLocationSnapshot lead,
                                        MeetingLocation meeting) {
        if (observed == null) {
            return NONE;
        }
        Coordinates pin = observed.latitude() == null || observed.longitude() == null ? null
                : new Coordinates(observed.latitude(), observed.longitude());
        return new LocationDifference(
                PlaceComparison.differs(observed.addressText(), observed.wardCode(), pin, lead.addressDetail(),
                        lead.wardCode(), lead.coordinates()),
                PlaceComparison.differs(observed.addressText(), observed.wardCode(), pin, meeting.addressText(),
                        meeting.wardCode(), meeting.coordinates()));
    }
}
