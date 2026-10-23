package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.policy.SurveyText;

/**
 * What makes a visit record a proxy record (survey-schedule US-3 AC-1a, BR-9, spec A-8): why someone other than the
 * performer enters it, and on what basis.
 */
public record ProxyRecord(String reason, Basis basis) {

    public ProxyRecord {
        reason = SurveyText.require("proxy.reason", reason, SurveyAppointment.MAX_REASON_LENGTH);
        if (basis == null) {
            throw new InvalidSurveyValueException("proxy.basis", "proxy.basis is required.");
        }
    }

    @Override
    public String toString() {
        return "ProxyRecord[hidden]";
    }
}
