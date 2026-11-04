package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.policy.checklist.RevisionStartBlock;

/** Whether the viewer can start a new revision (survey-checklist submission spec 7.2). */
@Schema(description = "Whether the viewer can start a new revision on the current visit")
public record ChecklistRevisionStartResponse(
        boolean allowed,
        @Schema(nullable = true, description = "The revision the new one follows, the path of the start")
        String checklistId,
        @Schema(description = "The viewer did not hold that revision, so the start needs an intake declaration")
        boolean intakeRequired,
        @Schema(nullable = true)
        RevisionStartBlock blockedBy) {
}
