package vn.sunvibe.survey.core.presentation.dto.response.media;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer of a photo create (survey-media spec 7.1): the photo and the set version after the change. */
@Schema(description = "The created survey photo record (201) or its replay (200)")
public record SurveyMediaCreateResponse(SurveyMediaItem media, long setVersion) {
}
