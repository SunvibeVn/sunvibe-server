package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.media.MediaRef;
import vn.sunvibe.survey.core.domain.model.media.PlacedMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Survey photo rows (survey-media spec 5.2, 5.4). A locking read is the first load of a row in its persistence context
 * (W16 5.5); every conditional update also bumps the row version and clears the persistence context, so no stale
 * managed photo is returned afterwards. Sweep finders repeat the partial-index predicates as literals.
 */
public interface SurveyMediaDomainRepository {

    /**
     * Insert a new photo and flush; the aggregate gets version 0.
     *
     * @throws vn.sunvibe.survey.core.domain.exception.MediaDuplicateContentException on the A-16 (a) backstop
     * @throws vn.sunvibe.survey.core.domain.exception.MediaUploadKeyTakenException on the upload-key unique key
     */
    void insert(SurveyMedia media);

    /**
     * Update the photo row guarded by its version and flush; the aggregate gets the new version. A row changed since
     * it was read fails with an optimistic locking failure.
     */
    void update(SurveyMedia media);

    /** {@code FOR UPDATE} on the photos, taken in id order (spec 5.4); unknown ids are skipped. */
    List<SurveyMedia> lockForUpdate(Collection<SurveyMediaId> ids);

    /** Every photo of the checklist by number, with its variants; a plain read. */
    List<SurveyMedia> findForChecklist(String checklistId);

    /**
     * spec A-9: the id, placement and category of every selected photo of the checklist, by a scalar query, so no
     * photo row is loaded before its lock.
     */
    List<PlacedMedia> findSelectedPlacements(String checklistId);

    /** spec 4.8: the ids of the uploader's AWAITING_CONTENT and RECEIVING photos of the checklist (scalar query). */
    List<SurveyMediaId> findOpenUploadIds(String checklistId, String uploaderUserId);

    /** A plain read of a photo with its variants, for checks before a conditional update (spec 4.4 step 2). */
    Optional<SurveyMedia> findById(SurveyMediaId id);

    /** Every uploader of a photo of these revisions, removed ones included (submission plan PD-2). */
    List<ChecklistPerson> findUploaders(Collection<String> checklistIds);

    /** Everyone who removed a photo of these revisions, by hand or by a cascade (submission plan PD-2). */
    List<ChecklistPerson> findRemovers(Collection<String> checklistIds);

    /** The photos with these ids, with their variants, in id order; unknown ids are skipped. */
    List<SurveyMedia> findByIds(Collection<SurveyMediaId> ids);

    /** The checklist of a photo by a scalar query, without loading it (spec 5.4 paths without a lead lock). */
    Optional<String> findChecklistIdById(SurveyMediaId id);

    /** The photo of this uploader's key on the checklist (spec 4.4 step 1 replay). */
    Optional<SurveyMedia> findByUploadKey(String checklistId, String uploadedByUserId, String uploadKey);

    /**
     * spec A-16 (b): photos with this hash and committed content on checklists of the same lead whose visit is not the
     * given checklist's visit (through {@code survey_checklists.lead_id}).
     */
    List<SurveyMedia> findCommittedHashOnOtherVisits(String leadId, String checklistId, String sha256);

    /**
     * spec 4.4 step 2: AWAITING_CONTENT, not removed and created after {@code createdAfter} becomes RECEIVING with
     * the claim; false when no row matched.
     */
    boolean claimIfAwaiting(SurveyMediaId id, String token, String attemptKey, Instant now, Instant createdAfter);

    /** spec 4.4 step 2: a RECEIVING photo holding this claim token goes back to AWAITING_CONTENT. */
    boolean releaseClaimIfToken(SurveyMediaId id, String token);

    /**
     * spec 4.4 step 4: the checklist's AWAITING_CONTENT photos created at or before the cutoff become UPLOAD_EXPIRED
     * (CONTENT_NOT_RECEIVED); returns how many. The caller holds the set lock.
     */
    int expireStale(String checklistId, Instant createdAtOrBefore, Instant now);

    /** spec 4.4 step 4: AWAITING_CONTENT photos created at or before the cutoff, oldest first. */
    List<MediaRef> findAwaitingCreatedBefore(Instant cutoff, int limit);

    /** spec 4.4 step 4: RECEIVING photos whose claim started at or before the cutoff, oldest first. */
    List<MediaRef> findReceivingStartedBefore(Instant cutoff, int limit);

    /** spec 4.4 step 4: PROCESSING photos received at or before the cutoff, oldest first. */
    List<MediaRef> findProcessingReceivedBefore(Instant cutoff, int limit);

    /** spec 4.4 step 4: sources not deleted after an outcome, received at or before the cutoff, oldest first. */
    List<MediaRef> findSourcesNotDeletedBefore(Instant cutoff, int limit);

    /** spec 4.7: content deletions requested at or before the cutoff and not done, oldest first. */
    List<MediaRef> findDeletionsRequestedBefore(Instant cutoff, int limit);

    /**
     * spec A-17: deletes at most {@code limit} UPLOAD_EXPIRED records of the checklist finished at or before the
     * cutoff; returns how many. The caller holds the set lock.
     */
    int deleteExpiredRecords(String checklistId, Instant finishedAtOrBefore, int limit);

    /** spec 4.4 step 4: the quarantine keys a RECEIVING (attempt) or PROCESSING (source) photo references. */
    java.util.Set<String> findQuarantineKeysInUse();

    /** spec 5.1: READY photos not marked content missing and not content deleted, by id after the given one. */
    List<MediaRef> findReadyWithoutContentMissing(String afterIdOrNull, int limit);

    /** spec A-17: UPLOAD_EXPIRED records finished at or before the cutoff, oldest first. */
    List<MediaRef> findExpiredFinishedBefore(Instant cutoff, int limit);
}
