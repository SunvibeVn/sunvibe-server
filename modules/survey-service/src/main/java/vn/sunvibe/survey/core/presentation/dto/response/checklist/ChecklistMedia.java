package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

/** The media block of a checklist (survey-media spec A-1, 7.1). */
@Schema(description = "Whether the checklist's photos are available and this viewer could upload now")
public record ChecklistMedia(
        @Schema(description = "Uploads are on or the checklist already has photos; readiness then lists evidence"
                + " per required pair")
        boolean available,
        @Schema(description = "Uploads on, intake not paused and the viewer could create a photo now; the same value"
                + " as uploadEnabled of the media list")
        boolean uploadEnabled,
        @Schema(description = "The set version the evidence items of readiness were computed from; 0 without photos")
        long setVersion) {
}
