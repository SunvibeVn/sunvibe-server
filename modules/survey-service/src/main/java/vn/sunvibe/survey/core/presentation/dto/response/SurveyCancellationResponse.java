package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;

import java.time.Instant;

/** How the appointment was cancelled (survey-schedule US-2 AC-2): reason, description, who and when. */
@Schema(description = "The cancellation of the appointment")
public record SurveyCancellationResponse(CancelReason cancelReason, String description, String cancelledByUserId,
                                         String cancelledByName, Instant cancelledAt) {
}
