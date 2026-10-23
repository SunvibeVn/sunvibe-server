package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** One page of the survey list; {@code totalElements} counts every match over all pages. */
@Schema(description = "One page of the survey list")
public record SurveyPageResponse(List<SurveyListItemResponse> items, int page, int size, long totalElements) {
}
