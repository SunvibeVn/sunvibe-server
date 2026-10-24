package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** A checkpoint: every group as it was just before a takeover (survey-checklist spec 4.5). */
@Schema(description = "A checkpoint: every group as it was just before a takeover")
public record ChecklistSnapshotResponse(
        ChecklistGroups groups) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistSnapshotResponse[hidden]";
    }
}
