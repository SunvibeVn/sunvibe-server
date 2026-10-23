package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** The answer of the overlap pre-check (survey-schedule spec A-10). */
@Schema(description = "Scheduled appointments of the lead's surveyor that overlap the planned time")
public record SurveyOverlapsResponse(List<SurveyOverlapResponse> overlaps) {
}
