package vn.sunvibe.survey.core.domain.repository.media;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobClaim;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

/**
 * The processing and content-deletion jobs of photos (survey-media spec 4.4 step 3, 4.7, 5.2): one per kind. Every
 * change of a claimed job is fenced by its lease token: it applies only while the job is PENDING and still carries the
 * token, so a worker whose lease ran out changes nothing.
 */
public interface SurveyMediaJobDomainRepository {

    /** survey-media spec 4.4 step 2: the PROCESS job of a committed photo, PENDING from {@code dueAt} with no attempt yet. */
    void upsertProcess(SurveyMediaId mediaId, Instant dueAt, Instant now);

    /** survey-media spec 4.7: the DELETE_CONTENT job of a removed READY photo, PENDING from {@code dueAt}. */
    void upsertDeleteContent(SurveyMediaId mediaId, Instant dueAt, Instant now);

    /** Every job of the photo. */
    List<MediaJob> findByMedia(SurveyMediaId mediaId);

    /**
     * survey-media spec 4.4 step 3, A-27: in the caller's short transaction, the next due PROCESS job ({@code FOR UPDATE OF j SKIP
     * LOCKED} through the partial index on kind and due time), ordered by its uploader's processing milliseconds of the
     * hour, skipping the given uploaders; it gets the new lease token, the lease end as due time and one more attempt.
     */
    Optional<MediaJobClaim> claimNextProcess(Instant now, Instant hourStart, Collection<String> excludedUploaders,
                                             String token, Instant leaseUntil);

    /** survey-media spec A-7: the lease of a working job extended; false when the job no longer carries the token. */
    boolean renewLease(long jobId, String token, Instant leaseUntil, Instant now);

    /**
     * survey-media spec 4.7: claims the earliest due PENDING DELETE_CONTENT job ({@code FOR UPDATE SKIP LOCKED} through the partial
     * index on kind and due time); it gets the lease token, the lease end as due time and one more attempt.
     */
    Optional<MediaJob> claimNextDeleteContent(Instant now, String token, Instant leaseUntil);

    /** survey-media spec 4.4 step 4: the sweeper closes a stale PROCESSING photo's job, whatever lease it has. */
    void closeProcess(SurveyMediaId mediaId, MediaJobState state, String code, Instant now);

    /** survey-media spec A-18: whether a PENDING job of the kind is due now (a stale heartbeat then pauses intake). */
    boolean anyDue(MediaJobKind kind, Instant now);

    /** {@code FOR UPDATE} on the job row, after the set and photo rows (survey-media spec 5.4). */
    Optional<MediaJob> lockForUpdate(long jobId);

    /** Fenced: the job ends in the state with its last error code. */
    boolean finish(long jobId, String token, MediaJobState state, String errorCodeOrNull, Instant now);

    /** Fenced: PENDING again from {@code dueAt}, the lease dropped, with the error code and the limit hits so far. */
    boolean retry(long jobId, String token, Instant dueAt, String errorCode, int limitHits, Instant now);

    /** Fenced: PENDING again from {@code dueAt} without counting the attempt (survey-media spec A-18: the scan step was blocked). */
    boolean defer(long jobId, String token, Instant dueAt, Instant now);
}
