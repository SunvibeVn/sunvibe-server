package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Who held (was the surveyor of) an appointment from when to when; {@code to} is null for the current holder. */
@Schema(description = "A holder of the appointment and the time they held it")
public record SurveyHolderResponse(String userId, String name, Instant from, Instant to) {
}
