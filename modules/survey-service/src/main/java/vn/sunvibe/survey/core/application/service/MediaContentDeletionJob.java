package vn.sunvibe.survey.core.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.MediaSetLocks.LockedSets;
import vn.sunvibe.survey.core.application.shared.MediaSetLocks;
import vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ListedObject;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ObjectPage;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.media.MediaRemovalPolicy;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;

/**
 * The content deletion of a removed READY photo (survey-media spec 4.7, A-17, A-23): the job is claimed in its own
 * transaction; under the set, photo and job locks it re-checks that the photo is still asked to be deleted, not held,
 * not deleted yet and referenced by no submission (checklist submission spec I-5); it deletes every object under {@code media/<mediaId>/} outside the locks and checks that a
 * listing of that prefix is empty; then, in a short transaction, sets {@code contentDeletedAt} and bumps the set
 * version. Metadata and hashes stay (I-4). A store failure or an object still listed retries the job later; it is
 * retried until done. Each run records the deletion heartbeat (A-18).
 */
@Service
public class MediaContentDeletionJob {

    /** spec A-17: the deletion is due 10 minutes after the request, so reads already streaming end. */
    public static final Duration DELAY = MediaRemovalPolicy.CONTENT_DELETION_DELAY;
    static final Duration LEASE = Duration.ofMinutes(5);
    static final Duration RETRY_AFTER = Duration.ofMinutes(5);
    private static final int PAGE = 1000;

    private static final Logger log = LoggerFactory.getLogger(MediaContentDeletionJob.class);

    private final SurveyMediaJobDomainRepository jobs;
    private final SurveyMediaDomainRepository media;
    private final MediaSetLocks setLocks;
    private final EvidenceReferencePort references;
    private final MediaObjectStore store;
    private final IntakeHealthRepository health;
    private final MediaClock clock;
    private final IdGenerator<String> ulids;
    private final TransactionTemplate transaction;

    public MediaContentDeletionJob(SurveyMediaJobDomainRepository jobs, SurveyMediaDomainRepository media,
                                   MediaSetLocks setLocks, EvidenceReferencePort references,
                                   MediaObjectStore store,
                                   IntakeHealthRepository health, MediaClock clock,
                                   @Qualifier("Ulid") IdGenerator<String> ulids,
                                   PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.media = media;
        this.setLocks = setLocks;
        this.references = references;
        this.store = store;
        this.health = health;
        this.clock = clock;
        this.ulids = ulids;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Runs due deletions until none is left. */
    public void drain() {
        try {
            while (runOnce()) {
                // keep claiming while deletions are due
            }
        } catch (RuntimeException e) {
            log.error("The survey media deletion job stopped a run", e);
        }
    }

    /** Records the heartbeat, claims one due deletion and does it; false when none was due. */
    public boolean runOnce() {
        Instant now = clock.now();
        try {
            health.beatDeletion(now);
        } catch (RuntimeException e) {
            log.error("Could not record the media deletion heartbeat", e);
        }
        String token = ulids.generateId();
        Optional<MediaJob> claimed = transaction.execute(status -> jobs.claimNextDeleteContent(now, token,
                now.plus(LEASE)));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        MediaJob job = claimed.get();
        try {
            delete(job, token);
        } catch (RuntimeException e) {
            // an unexpected failure: the lease runs out and the job is claimed again
            log.error("Survey media deletion job {} of photo {} failed unexpectedly", job.id(), job.mediaId(), e);
        }
        return true;
    }

    private void delete(MediaJob job, String token) {
        Boolean go = transaction.execute(status -> recheck(job, token));
        if (!Boolean.TRUE.equals(go)) {
            return;
        }
        String prefix = "media/" + job.mediaId().getValue() + "/";
        try {
            for (ListedObject object : listAll(prefix)) {
                store.delete(Bucket.MEDIA, object.key());
            }
            if (!listAll(prefix).isEmpty()) {
                retry(job, token, "LISTING_NOT_EMPTY");
                return;
            }
        } catch (StoreUnavailableException e) {
            retry(job, token, "STORE_UNAVAILABLE");
            return;
        }
        transaction.executeWithoutResult(status -> markDeleted(job, token));
    }

    /** Under the set, photo and job locks: the job still holds its lease and the photo still waits for deletion. */
    private boolean recheck(MediaJob job, String token) {
        Instant now = clock.now();
        Locked locked = lock(job, token);
        if (locked == null) {
            return false;
        }
        SurveyMedia photo = locked.photo();
        // checklist submission spec I-5, D3-3: the content of a photo a submission references is never deleted
        String endCode = references.isReferenced(photo.getId()) ? "REFERENCED" : photo.isHeld() ? "HELD"
                : photo.getContentDeletedAt() != null ? "ALREADY_DELETED"
                : photo.getContentDeleteRequestedAt() == null ? "NOT_REQUESTED" : null;
        if (endCode != null) {
            // spec A-23: a held photo keeps its content; its release asks for the deletion again
            jobs.finish(job.id(), token, "ALREADY_DELETED".equals(endCode) ? MediaJobState.DONE
                    : MediaJobState.CANCELLED, endCode, now);
            return false;
        }
        return true;
    }

    private void markDeleted(MediaJob job, String token) {
        Instant now = clock.now();
        Locked locked = lock(job, token);
        if (locked == null) {
            return;
        }
        SurveyMedia photo = locked.photo();
        if (references.isReferenced(photo.getId()) || photo.isHeld() || photo.getContentDeletedAt() != null
                || photo.getContentDeleteRequestedAt() == null) {
            jobs.finish(job.id(), token, MediaJobState.CANCELLED, "STATE_CHANGED", now);
            return;
        }
        photo.markContentDeleted(now);
        media.update(photo);
        jobs.finish(job.id(), token, MediaJobState.DONE, null, now);
        setLocks.bumpAll(locked.sets(), now);
    }

    private void retry(MediaJob job, String token, String code) {
        Instant now = clock.now();
        transaction.executeWithoutResult(status -> jobs.retry(job.id(), token, now.plus(RETRY_AFTER), code,
                job.limitHits(), now));
    }

    /** The locks of spec 5.4 for a path without a lead lock; null when the photo is gone or the lease was lost. */
    private Locked lock(MediaJob job, String token) {
        SurveyMediaId mediaId = job.mediaId();
        String checklistId = media.findChecklistIdById(mediaId).orElse(null);
        if (checklistId == null) {
            jobs.finish(job.id(), token, MediaJobState.CANCELLED, "PHOTO_GONE", clock.now());
            return null;
        }
        LockedSets sets = setLocks.lockFor(checklistId, mediaId)
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        SurveyMedia photo = media.lockForUpdate(List.of(mediaId)).stream().findFirst().orElse(null);
        MediaJob locked = jobs.lockForUpdate(job.id()).orElse(null);
        if (photo == null || locked == null || locked.state() != MediaJobState.PENDING
                || !token.equals(locked.leaseTokenOrNull())) {
            return null;
        }
        return new Locked(sets, photo);
    }

    private List<ListedObject> listAll(String prefix) {
        List<ListedObject> all = new ArrayList<>();
        String after = null;
        while (true) {
            ObjectPage page = store.list(Bucket.MEDIA, prefix, after, PAGE);
            all.addAll(page.objects());
            if (!page.truncated() || page.objects().isEmpty()) {
                return all;
            }
            after = page.objects().get(page.objects().size() - 1).key();
        }
    }

    private record Locked(LockedSets sets, SurveyMedia photo) {
    }
}
