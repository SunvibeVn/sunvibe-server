package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A link to a hazard of an earlier checklist of the same lead and requirement (survey-checklist BR-24). */
@Schema(description = "A link to a hazard of an earlier checklist of the same lead and requirement")
public record ChecklistHazardLink(
        @JsonProperty(required = true)
        String checklistId,
        @JsonProperty(required = true)
        String hazardId) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistHazardLink[" + hazardId + "]";
    }
}
