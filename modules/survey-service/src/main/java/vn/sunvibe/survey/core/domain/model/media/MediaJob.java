package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * A processing or content-deletion job of a photo (survey-media spec 4.4 step 3, 4.7, A-7): one row per photo and
 * kind, a lease token per attempt as the fencing token, the attempts and limit hits so far and the next due time.
 */
public record MediaJob(long id, SurveyMediaId mediaId, MediaJobKind kind, MediaJobState state, int attempts,
                       int limitHits, String leaseTokenOrNull, Instant dueAt, String lastErrorCodeOrNull,
                       Instant createdAt, Instant updatedAt) {
}
