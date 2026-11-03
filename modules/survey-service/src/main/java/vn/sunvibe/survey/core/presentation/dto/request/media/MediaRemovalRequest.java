package vn.sunvibe.survey.core.presentation.dto.request.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A holder's removal of a photo (survey-media spec 4.7, 7.1); every key present, unknown keys refused. */
@Schema(description = "Remove a survey photo from the checklist")
public record MediaRemovalRequest(
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "Needed when the photo's content was received and another person "
                + "uploaded it; otherwise null")
        MediaRemovalCorrectionRequest correction) {

    /** Staff text: never print it. */
    @Override
    public String toString() {
        return "MediaRemovalRequest[" + (correction == null ? "no correction" : "correction") + "]";
    }
}
