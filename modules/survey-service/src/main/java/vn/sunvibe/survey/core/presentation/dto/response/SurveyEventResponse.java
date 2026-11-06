package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEventType;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;

import java.time.Instant;

/** One event of the appointment's history, in order. */
@Schema(description = "One event of the appointment")
public record SurveyEventResponse(AppointmentEventType type, @Schema(nullable = true) String actorUserId,
                                  @Schema(nullable = true) String actorName, Instant occurredAt,
                                  @Schema(nullable = true) Integer revisionNo,
                                  @Schema(nullable = true) String fromSurveyorUserId,
                                  @Schema(nullable = true) String fromSurveyorName,
                                  @Schema(nullable = true) String toSurveyorUserId,
                                  @Schema(nullable = true) String toSurveyorName,
                                  @Schema(nullable = true) ConfirmationCause confirmationCause,
                                  @Schema(nullable = true) String note) {
}
