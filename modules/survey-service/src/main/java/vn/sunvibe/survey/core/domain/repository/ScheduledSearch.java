package vn.sunvibe.survey.core.domain.repository;

import java.util.Optional;

/** What the survey list searches: scheduled appointments of one surveyor, or of everyone when empty. */
public record ScheduledSearch(Optional<String> surveyorUserId) {

    public ScheduledSearch {
        if (surveyorUserId == null) {
            throw new IllegalArgumentException("surveyorUserId is required (empty for everyone).");
        }
    }
}
