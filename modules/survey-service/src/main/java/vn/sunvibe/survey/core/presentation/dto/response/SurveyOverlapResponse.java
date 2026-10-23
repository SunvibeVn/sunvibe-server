package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Another scheduled appointment of the same surveyor at the same time: only the request code and the time. */
@Schema(description = "An overlapping scheduled appointment of the same surveyor")
public record SurveyOverlapResponse(String appointmentId, String leadId, String requestCode, Instant startAt,
                                    int durationMinutes) {
}
