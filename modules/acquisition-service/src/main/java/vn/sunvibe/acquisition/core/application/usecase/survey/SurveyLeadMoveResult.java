package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/** The lead after a survey move: its status, its new version, its latest activity time and the History entry. */
public record SurveyLeadMoveResult(String status, long version, Instant lastActivityAt, SurveyLeadHistoryEntry entry) {
}
