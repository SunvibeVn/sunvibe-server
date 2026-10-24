package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.PetsPolicy;

/** The pets policy (survey-checklist BR-5). */
@Schema(description = "The pets policy")
public record ChecklistPets(
        @JsonProperty(required = true)
        PetsPolicy policy,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters, required for CONDITIONAL")
        String note) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistPets[" + policy + "]";
    }
}
