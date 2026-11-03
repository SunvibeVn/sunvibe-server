package vn.sunvibe.survey.core.presentation.dto.response.media;

import io.swagger.v3.oas.annotations.media.Schema;

/** A survey photo after an ADMIN hold or release (survey-media spec A-23, 7.1). */
@Schema(description = "The photo after a hold or a release; a no-op answers it unchanged with the same setVersion")
public record SurveyMediaStateResponse(SurveyMediaItem media, long setVersion) {
}
