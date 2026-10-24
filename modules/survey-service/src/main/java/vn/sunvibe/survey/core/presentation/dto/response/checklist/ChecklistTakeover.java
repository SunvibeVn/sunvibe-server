package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverKind;

import java.time.Instant;

/** A takeover of the draft (survey-checklist spec 4.5, 7.2). */
@Schema(description = "A takeover of the draft")
public record ChecklistTakeover(
        int takeoverNo,
        TakeoverKind kind,
        @Schema(nullable = true)
        String fromUserId,
        @Schema(nullable = true)
        String fromName,
        String toUserId,
        @Schema(nullable = true)
        String toName,
        String reason,
        ChecklistBasis basis,
        Instant occurredAt,
        @Schema(description = "Whether a checkpoint can be read (never for START_BY_OTHER)")
        boolean hasSnapshot) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistTakeover[" + takeoverNo + "]";
    }
}
