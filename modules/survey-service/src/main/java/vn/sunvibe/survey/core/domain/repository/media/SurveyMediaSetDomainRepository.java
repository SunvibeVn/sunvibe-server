package vn.sunvibe.survey.core.domain.repository.media;

import vn.sunvibe.survey.core.domain.model.media.MediaSet;

import java.time.Instant;
import java.util.Optional;

/** The media-set row of a checklist (survey-media spec 5.2, 5.4): plain version columns, changed under its lock. */
public interface SurveyMediaSetDomainRepository {

    /** survey-media spec 4.4 step 1: inserts the empty set, or nothing when the checklist has one. */
    void insertIgnoringExisting(String checklistId, Instant now);

    /** {@code FOR UPDATE} on the set row as its first load in the persistence context (survey-media spec 5.4). */
    Optional<MediaSet> lockForUpdate(String checklistId);

    /** A plain read of the set row (the list snapshot and the checklist read). */
    Optional<MediaSet> findForRead(String checklistId);

    /** Writes the set's version, last number and time; the caller holds the row lock. */
    void update(MediaSet set);
}
