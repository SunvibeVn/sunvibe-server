package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

import java.time.Instant;
import java.util.List;

/** A correcting save (survey-checklist spec 7.2). */
@Schema(description = "A correcting save")
public record ChecklistCorrection(
        String saveId,
        ChecklistGroup group,
        List<ChecklistCorrectedUnit> units,
        String reason,
        ChecklistBasis basis,
        String actorUserId,
        @Schema(nullable = true)
        String actorName,
        Instant savedAt) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistCorrection[" + saveId + "]";
    }
}
