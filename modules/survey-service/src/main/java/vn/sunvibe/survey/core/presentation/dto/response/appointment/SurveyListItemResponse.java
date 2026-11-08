package vn.sunvibe.survey.core.presentation.dto.response.appointment;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;
import vn.sunvibe.survey.core.domain.policy.appointment.ListGroup;

import java.time.Instant;

/**
 * One row of the survey list (survey-schedule US-4, survey-schedule spec 7.2): no phone, address, pin, access note or on-site
 * contact.
 */
@Schema(description = "One scheduled survey appointment in the survey list")
public record SurveyListItemResponse(String id, String leadId, String requestCode, String ownerContactName,
                                     String wardName, Instant startAt, int durationMinutes, String surveyorUserId,
                                     @Schema(nullable = true) String surveyorName, Confirmation confirmation,
                                     AppointmentStatus status,
                                     ListGroup group, AppointmentKind kind, boolean cannotBePerformed) {

    /** The confirmation as the list shows it. */
    @Schema(name = "SurveyListConfirmation", description = "The owner's confirmation in the survey list")
    public record Confirmation(ConfirmationState state, @Schema(nullable = true) ConfirmationCause cause,
                               boolean everConfirmed) {
    }

    @Override
    public String toString() {
        return "SurveyListItemResponse[" + id + "]";
    }
}
