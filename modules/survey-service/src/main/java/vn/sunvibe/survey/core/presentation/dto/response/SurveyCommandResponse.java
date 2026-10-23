package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The answer of a survey command (survey-schedule spec 7.2, A-23): the whole survey block, the lead's new state, the
 * History entry (null only for a no-op) and the overlapping appointments after a booking or a change of time.
 */
@Schema(description = "The answer of a survey command")
public record SurveyCommandResponse(SurveyBlockResponse survey, SurveyLeadStateResponse lead,
                                    SurveyHistoryEntryResponse historyEntry, List<SurveyOverlapResponse> overlaps) {
}
