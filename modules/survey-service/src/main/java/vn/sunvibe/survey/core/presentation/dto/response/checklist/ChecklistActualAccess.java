package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.AccessExtent;

/** The access actually had to a space (survey-checklist spec 4.6). */
@Schema(description = "The access actually had to a space")
public record ChecklistActualAccess(
        @JsonProperty(required = true)
        AccessExtent extent,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, required for ENTERED_PART")
        String note) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistActualAccess[" + extent + "]";
    }
}
