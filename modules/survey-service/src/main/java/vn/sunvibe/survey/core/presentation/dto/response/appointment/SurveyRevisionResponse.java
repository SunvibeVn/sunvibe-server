package vn.sunvibe.survey.core.presentation.dto.response.appointment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import vn.sunvibe.survey.core.presentation.dto.response.common.SurveyLocationResponse;

/** One version of the appointment's values (survey-schedule US-2 AC-1), with its reason, actor and time. */
@Schema(description = "One revision of the appointment's values")
public record SurveyRevisionResponse(int revisionNo, Instant validFrom, Instant startAt, int durationMinutes,
                                     SurveyLocationResponse meetingLocation,
                                     @Schema(nullable = true) String accessNote,
                                     @Schema(nullable = true) String onSiteContactName,
                                     @Schema(nullable = true) String onSiteContactRole,
                                     @Schema(nullable = true) String reason, String actorUserId,
                                     @Schema(nullable = true) String actorName) {
}
