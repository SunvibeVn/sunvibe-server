package vn.sunvibe.survey.core.presentation.dto.request.media;

import io.swagger.v3.oas.annotations.media.Schema;

/** An ADMIN's release of a photo's hold (survey-media spec A-23, 7.2): an empty object, unknown keys refused. */
@Schema(description = "Release the hold of a survey photo: an empty object")
public record MediaHoldReleaseRequest() {
}
