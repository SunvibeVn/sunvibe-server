package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** The condition group: its version, last save and content (survey-checklist spec 7.2). */
@Schema(description = "The condition group: its version, last save and content")
public record ChecklistConditionGroup(
        long version,
        @Schema(nullable = true)
        Instant updatedAt,
        @Schema(nullable = true)
        String updatedByUserId,
        @Schema(nullable = true)
        String updatedByName,
        ChecklistConditionContent content) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistConditionGroup[" + version + "]";
    }
}
