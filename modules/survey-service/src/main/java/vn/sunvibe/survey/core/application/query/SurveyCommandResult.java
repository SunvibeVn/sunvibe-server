package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;

import java.util.List;

/**
 * The answer of a survey command (survey-schedule spec 7.2, A-23): the whole survey block, the lead's new state, the
 * History entry written (null only for a no-op, A-6) and the overlapping appointments after a booking or a change of
 * time (empty otherwise).
 */
public record SurveyCommandResult(SurveyBlock survey, LeadState lead, LeadHistoryEntry historyEntry,
                                  List<OverlapView> overlaps) {

    public SurveyCommandResult {
        overlaps = List.copyOf(overlaps);
    }
}
