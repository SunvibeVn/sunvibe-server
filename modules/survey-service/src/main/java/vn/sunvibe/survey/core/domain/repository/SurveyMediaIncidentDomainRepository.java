package vn.sunvibe.survey.core.domain.repository;

import java.time.Instant;

import vn.sunvibe.survey.core.domain.model.media.MediaIncidentKind;

/** The minimal incident records of overdue deletions (survey-media spec A-18, 5.2): one open row per kind. */
public interface SurveyMediaIncidentDomainRepository {

    /**
     * Opens the kind's incident, or updates the open one ({@code ON CONFLICT} on the open kind): the first overdue
     * time stays the earliest seen, the count is the latest.
     */
    void recordOverdue(MediaIncidentKind kind, Instant firstOverdueAt, int count, Instant appNow);

    /** Resolves every open incident (a resuming sweep); returns how many. */
    int resolveOpen(Instant appNow);
}
