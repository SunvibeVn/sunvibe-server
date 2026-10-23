package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The lead History entry a survey command wrote, with the fields of the lead queue's activity response
 * (survey-schedule spec 7.2): no text, only the appointment reference; note, next action and assignees are null.
 */
@Schema(description = "The lead History entry written by a survey command")
public record SurveyHistoryEntryResponse(
        @Schema(allowableValues = {"SURVEY_BOOKED", "SURVEY_CHANGED", "SURVEY_CONFIRMED", "SURVEY_UNCONFIRMED",
                "SURVEY_CANCELLED", "SURVEY_VISITED", "SURVEY_RESUMED"})
        String type,
        String actorUserId,
        String actorName,
        @Schema(description = "Null when the lead kept its status")
        String fromStatus,
        @Schema(description = "Null when the lead kept its status")
        String toStatus,
        String fromAssigneeId,
        String toAssigneeId,
        String fromAssigneeName,
        String toAssigneeName,
        String note,
        Instant nextActionAt,
        Instant occurredAt,
        String referenceId,
        Integer referenceNo) {
}
