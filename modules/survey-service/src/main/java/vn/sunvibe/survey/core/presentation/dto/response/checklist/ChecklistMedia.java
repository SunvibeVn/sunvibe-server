package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The media block of a checklist (survey-media spec A-1, 7.1); it also lists the refused photos of the previous revision
 * that this revision does not hold (survey-checklist SV-310 spec 3.4).
 */
@Schema(description = "Whether the checklist's photos are available and this viewer could upload now, and the refused"
        + " photos of the previous revision that this revision does not hold")
public record ChecklistMedia(
        @Schema(description = "Uploads are on or the checklist already has photos; readiness then lists evidence"
                + " per required pair")
        boolean available,
        @Schema(description = "Uploads on, intake not paused and the viewer could create a photo now; the same value"
                + " as uploadEnabled of the media list")
        boolean uploadEnabled,
        @Schema(description = "The set version the evidence items of readiness were computed from; 0 without photos")
        long setVersion,
        @Schema(description = "The photos of the previous revision's frozen set that a decision on an earlier revision"
                + " of the visit did not accept and that this revision does not hold, one per photo with its newest"
                + " refusal, newest revision first; empty when none")
        List<ChecklistRefusedPhoto> refusedNotCarried) {
}
