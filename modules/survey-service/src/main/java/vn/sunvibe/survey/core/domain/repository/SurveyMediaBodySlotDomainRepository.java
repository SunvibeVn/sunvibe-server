package vn.sunvibe.survey.core.domain.repository;

import java.time.Instant;

/**
 * Content bodies in flight per user, exact across instances (survey-media spec A-3, SV-017): a row per body, taken
 * under the user's advisory lock, freed in {@code finally}; expired rows are ignored and swept.
 */
public interface SurveyMediaBodySlotDomainRepository {

    /** Inserts a slot row and returns its id. */
    long take(String userId, String instanceId, Instant now, Instant expiresAt);

    /** Deletes the slot row. */
    void free(long slotId);

    /** The user's slots not expired at {@code now}. */
    int countLive(String userId, Instant now);

    /** Deletes the slots expired at {@code now}; returns how many. */
    int deleteExpired(Instant now);
}
