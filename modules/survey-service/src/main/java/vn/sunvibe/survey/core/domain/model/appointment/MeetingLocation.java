package vn.sunvibe.survey.core.domain.model.appointment;

import vn.sunvibe.survey.core.domain.policy.PlaceComparison;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

/**
 * Where staff meet the owner (survey-schedule BR-7, spec C-3b): an address, a ward of the catalog with its province
 * and an optional pin; the lead's place unless staff give another one. Province, ward and pin were checked by the
 * intake location rules before (survey-schedule spec A-15). Private location data: never logged.
 */
public record MeetingLocation(String addressText, String provinceCode, String provinceName, String wardCode,
                              String wardName, Coordinates coordinates) {

    public static final int MAX_ADDRESS_LENGTH = 255;

    public MeetingLocation {
        if (addressText == null || provinceCode == null || provinceName == null || wardCode == null
                || wardName == null) {
            throw new IllegalArgumentException("A meeting place needs address, province and ward.");
        }
    }

    /**
     * A place staff typed: the address is 1..255 characters without a phone number (survey-schedule BR-4, A-14).
     *
     * @throws vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException field
     *                                                                             {@code meetingLocation.addressText}
     */
    public static MeetingLocation of(String addressText, String provinceCode, String provinceName, String wardCode,
                                     String wardName, Coordinates coordinatesOrNull) {
        return new MeetingLocation(SurveyText.require("meetingLocation.addressText", addressText, MAX_ADDRESS_LENGTH),
                provinceCode, provinceName, wardCode, wardName, coordinatesOrNull);
    }

    /** The lead's own place, as the default meeting place (survey-schedule US-1 AC-2). */
    public static MeetingLocation fromLead(LeadLocationSnapshot lead) {
        return new MeetingLocation(lead.addressDetail(), lead.provinceCode(), lead.provinceName(), lead.wardCode(),
                lead.wardName(), lead.coordinates());
    }

    /**
     * survey-schedule spec A-16: the place differs from the lead when its address (NFC, single-spaced, lower case),
     * its ward or its pin differs.
     */
    public boolean differsFrom(LeadLocationSnapshot lead) {
        return PlaceComparison.differs(addressText, wardCode, coordinates, lead.addressDetail(), lead.wardCode(),
                lead.coordinates());
    }

    @Override
    public String toString() {
        return "MeetingLocation[hidden]";
    }
}
