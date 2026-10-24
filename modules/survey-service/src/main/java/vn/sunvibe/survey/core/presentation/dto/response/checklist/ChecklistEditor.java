package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Someone who saved the draft, with their latest save. */
@Schema(description = "Someone who saved the draft, with their latest save")
public record ChecklistEditor(
        String userId,
        @Schema(nullable = true)
        String name,
        Instant lastSavedAt) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistEditor[" + userId + "]";
    }
}
