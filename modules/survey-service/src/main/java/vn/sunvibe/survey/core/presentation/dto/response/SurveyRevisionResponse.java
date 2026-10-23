package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** One version of the appointment's values (survey-schedule US-2 AC-1), with its reason, actor and time. */
@Schema(description = "One revision of the appointment's values")
public record SurveyRevisionResponse(int revisionNo, Instant validFrom, Instant startAt, int durationMinutes,
                                     SurveyLocationResponse meetingLocation, String accessNote,
                                     String onSiteContactName, String onSiteContactRole, String reason,
                                     String actorUserId, String actorName) {
}
