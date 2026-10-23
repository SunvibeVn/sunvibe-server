package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEventType;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;

import java.time.Instant;

/** One event of the appointment's history, in order. */
@Schema(description = "One event of the appointment")
public record SurveyEventResponse(AppointmentEventType type, String actorUserId, String actorName, Instant occurredAt,
                                  Integer revisionNo, String fromSurveyorUserId, String fromSurveyorName,
                                  String toSurveyorUserId, String toSurveyorName,
                                  ConfirmationCause confirmationCause, String note) {
}
