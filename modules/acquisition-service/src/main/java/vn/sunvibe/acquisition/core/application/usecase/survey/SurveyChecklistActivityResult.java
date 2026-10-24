package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/** The lead's latest activity time read back after a checklist History entry, and that entry. */
public record SurveyChecklistActivityResult(Instant lastActivityAt, SurveyChecklistHistoryEntry entry) {
}
