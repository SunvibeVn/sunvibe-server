package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One resumption of the lead's survey work (survey-schedule BR-11). */
@Schema(description = "A resumption of the lead's survey work")
public record SurveyResumptionResponse(String visitAppointmentId, int visitAppointmentNo, String reason,
                                       SurveyBasisResponse basis, String actorUserId, String actorName,
                                       Instant occurredAt) {
}
