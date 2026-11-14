package vn.sunvibe.survey.core.presentation.dto.response.media;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;

/** The removal of a survey photo (survey-media spec 7.2, 4.7, A-9); ids are opaque strings in the API (ADR-006). */
@Schema(description = "The removal of a survey photo")
public record SurveyMediaRemoval(
        Instant at,
        String byUserId,
        @Schema(nullable = true)
        String byName,
        RemovalKind kind,
        @Schema(nullable = true, description = "The correction BR-12 asked for when another uploader's photo was "
                + "removed")
        SurveyMediaCorrection correction,
        @Schema(nullable = true, description = "Opaque id of the G1 save of a CASCADE removal; equals saveId of that "
                + "save's checklist correction when it recorded one")
        String bySaveId) {

    /** Staff text: ids only. */
    @Override
    public String toString() {
        return "SurveyMediaRemoval[" + kind + " by " + byUserId + "]";
    }
}
