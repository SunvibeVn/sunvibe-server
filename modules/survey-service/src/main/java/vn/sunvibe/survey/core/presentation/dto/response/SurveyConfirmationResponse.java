package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;

import java.time.Instant;

/**
 * The owner's confirmation (survey-schedule US-1 AC-5): "needs reconfirmation" is UNCONFIRMED with
 * {@code everConfirmed}; {@code cause} is null exactly when CONFIRMED.
 */
@Schema(description = "The owner's confirmation of the appointment")
public record SurveyConfirmationResponse(ConfirmationState state, ConfirmationCause cause, boolean everConfirmed,
                                         String changedByUserId, String changedByName, Instant changedAt,
                                         String note) {
}
