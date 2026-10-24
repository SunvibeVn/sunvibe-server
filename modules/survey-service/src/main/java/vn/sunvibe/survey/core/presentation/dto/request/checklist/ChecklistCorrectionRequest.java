package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** Why a save corrects values someone else observed or heard, and on what basis (survey-checklist BR-22, spec 4.5). */
@Schema(description = "Why a save corrects values someone else observed or heard, and on what basis")
public record ChecklistCorrectionRequest(
        @JsonProperty(required = true)
        @Schema(description = "1..500 characters, no phone number")
        String reason,
        @JsonProperty(required = true)
        ChecklistBasisRequest basis) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistCorrectionRequest[hidden]";
    }
}
