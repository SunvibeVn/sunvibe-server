package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

import java.math.BigDecimal;

/**
 * A meeting place as staff typed it (survey-schedule BR-7): address (1..255 characters, no phone number), province
 * and ward codes and an optional pin. The catalog and the pin are checked by the lead's location rules next.
 *
 * @throws InvalidSurveyValueException field {@code meetingLocation.addressText}, {@code .provinceCode} or
 *                                     {@code .wardCode}
 */
public record MeetingPlaceInput(String addressText, String provinceCode, String wardCode, BigDecimal latitude,
                                BigDecimal longitude) {

    public MeetingPlaceInput {
        addressText = SurveyText.require("meetingLocation.addressText", addressText,
                MeetingLocation.MAX_ADDRESS_LENGTH);
        if (provinceCode == null || provinceCode.isBlank()) {
            throw new InvalidSurveyValueException("meetingLocation.provinceCode",
                    "meetingLocation.provinceCode is required.");
        }
        if (wardCode == null || wardCode.isBlank()) {
            throw new InvalidSurveyValueException("meetingLocation.wardCode", "meetingLocation.wardCode is required.");
        }
    }

    @Override
    public String toString() {
        return "MeetingPlaceInput[hidden]";
    }
}
