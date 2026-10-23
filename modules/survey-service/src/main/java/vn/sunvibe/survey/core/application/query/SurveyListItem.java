package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;
import vn.sunvibe.survey.core.domain.policy.ListGroup;

import java.time.Instant;

/**
 * One row of the survey list (survey-schedule US-4, spec 7.2): no phone, address, pin, access note or on-site contact.
 * The owner's name is personal data: never logged.
 */
public record SurveyListItem(String id, String leadId, String requestCode, String ownerContactName, String wardName,
                             Instant startAt, int durationMinutes, String surveyorUserId, String surveyorName,
                             ConfirmationState confirmationState, ConfirmationCause confirmationCause,
                             boolean everConfirmed, AppointmentStatus status, ListGroup group, AppointmentKind kind,
                             boolean cannotBePerformed) {

    @Override
    public String toString() {
        return "SurveyListItem[" + id + "]";
    }
}
