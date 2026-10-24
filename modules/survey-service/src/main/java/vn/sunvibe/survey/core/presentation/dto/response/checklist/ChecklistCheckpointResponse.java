package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** A takeover and its checkpoint (survey-checklist spec 7.1). */
@Schema(description = "A takeover and its checkpoint")
public record ChecklistCheckpointResponse(
        ChecklistTakeover takeover,
        ChecklistSnapshotResponse snapshot) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistCheckpointResponse[" + takeover + "]";
    }
}
