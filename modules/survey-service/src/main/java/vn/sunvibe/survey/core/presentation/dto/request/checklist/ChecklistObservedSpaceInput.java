package vn.sunvibe.survey.core.presentation.dto.request.checklist;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** A viewed rentable space of G1, as sent; a new space carries a client ULID (survey-checklist spec 4.6, A-23). */
@Schema(description = "A viewed rentable space of G1, as sent; a new space carries a client ULID")
public record ChecklistObservedSpaceInput(
        @JsonProperty(required = true)
        @Schema(description = "ULID; an id not stored yet is a new space")
        String spaceId,
        @JsonProperty(required = true)
        @Schema(description = "1..20 characters, unique ignoring case")
        String code,
        @JsonProperty(required = true)
        @Schema(description = "1..100 characters")
        String location,
        @JsonProperty(required = true)
        @Schema(nullable = true, description = "1..500 characters")
        String description) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistObservedSpaceInput[" + spaceId + "]";
    }
}
