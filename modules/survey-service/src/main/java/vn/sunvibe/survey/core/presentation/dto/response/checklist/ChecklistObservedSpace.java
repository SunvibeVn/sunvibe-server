package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** A viewed rentable space of G1, as read (survey-checklist spec 4.6). */
@Schema(description = "A viewed rentable space of G1, as read")
public record ChecklistObservedSpace(
        String spaceId,
        String code,
        String location,
        @Schema(nullable = true)
        String description,
        @Schema(nullable = true)
        ChecklistUnitMeta meta) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistObservedSpace[" + spaceId + "]";
    }
}
