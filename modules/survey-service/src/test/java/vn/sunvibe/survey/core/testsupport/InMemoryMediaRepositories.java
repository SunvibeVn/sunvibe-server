package vn.sunvibe.survey.core.testsupport;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.survey.core.domain.exception.media.MediaDuplicateContentException;
import vn.sunvibe.survey.core.domain.exception.media.MediaUploadKeyTakenException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.domain.model.media.PlacedMedia;
import vn.sunvibe.survey.core.domain.model.media.HourlyUsage;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJob;
import vn.sunvibe.survey.core.domain.model.media.MediaJobClaim;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;
import vn.sunvibe.survey.core.domain.model.media.MediaRef;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SurveySecurityEvent;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.repository.media.MediaCountersRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaBodySlotDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaVariantDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.OptionalLong;
import java.util.function.Predicate;

/**
 * Hand-written fakes of the survey-media repositories. Lock and read calls go to the shared journal ("media:lock",
 * "set:lock", "user:lock", "media:read", ...), so a service test can assert the lock order of spec 5.4 and that no
 * row is read before its lock. Stored photos are copies: a test sees only what a service wrote.
 */
public final class InMemoryMediaRepositories {

    private InMemoryMediaRepositories() {
    }

    /** A copy of the photo, as a reload would give it. */
    static SurveyMedia copy(SurveyMedia m) {
        return SurveyMedia.load(m.getId(), m.getChecklistId(), m.getUploadKey(), m.getMediaNo(), m.getPlacement(),
                m.getCategory(), m.getCaption(), m.getUploadedByUserId(), m.getCreatedAt(), m.getDeclaredSizeBytes(),
                m.getSourceSha256(), m.getStatus(), m.getStatusReason(), m.getReceiveToken(), m.getReceiveKey(),
                m.getReceiveStartedAt(), m.getContentReceivedAt(), m.getSourceStorageKey(), m.getSourceDeletedAt(),
                m.getSourceFormat(), m.getReadyAt(), m.getFinishedAt(), m.getRemoval(), m.getHold(),
                m.getContentDeleteRequestedAt(), m.getContentDeletedAt(), m.getContentMissingAt(), m.getVariants(),
                m.getVersion());
    }

    /** Photos by id; both unique keys of spec 5.2 refuse like the database. */
    public static class Media implements SurveyMediaDomainRepository {

        private final Map<SurveyMediaId, SurveyMedia> stored = new LinkedHashMap<>();
        private final List<String> journal;
        /** Runs at the start of {@link #insert}: a test puts a concurrent twin there, as if it committed meanwhile. */
        public Consumer<SurveyMedia> beforeInsert = media -> { };
        /** Checklist id to its lead and visit, for the other-visit finder. */
        public final Map<String, String[]> checklistLeadAndVisit = new HashMap<>();

        public Media(List<String> journal) {
            this.journal = journal;
        }

        public List<SurveyMedia> all() {
            return stored.values().stream().map(InMemoryMediaRepositories::copy).toList();
        }

        public Optional<SurveyMedia> stored(SurveyMediaId id) {
            return Optional.ofNullable(stored.get(id)).map(InMemoryMediaRepositories::copy);
        }

        /** Puts a photo as if another transaction had stored it. */
        public void put(SurveyMedia media) {
            if (media.getVersion() == null) {
                media.markPersisted(0L);
            }
            stored.put(media.getId(), copy(media));
        }

        @Override
        public void insert(SurveyMedia media) {
            journal.add("media:insert");
            beforeInsert.accept(media);
            boolean sameKey = stored.values().stream().anyMatch(o -> o.getChecklistId().equals(media.getChecklistId())
                    && o.getUploadedByUserId().equals(media.getUploadedByUserId())
                    && o.getUploadKey().equals(media.getUploadKey()));
            if (sameKey) {
                throw new MediaUploadKeyTakenException(null);
            }
            boolean sameSelectedHash = media.isSelected() && stored.values().stream()
                    .anyMatch(o -> o.isSelected() && o.getChecklistId().equals(media.getChecklistId())
                            && o.getSourceSha256().equals(media.getSourceSha256()));
            if (sameSelectedHash) {
                throw new MediaDuplicateContentException(null);
            }
            media.markPersisted(0L);
            stored.put(media.getId(), copy(media));
        }

        @Override
        public void update(SurveyMedia media) {
            journal.add("media:update");
            SurveyMedia current = stored.get(media.getId());
            if (!current.getVersion().equals(media.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(SurveyMedia.class, media.getId().getValue());
            }
            media.markPersisted(current.getVersion() + 1);
            stored.put(media.getId(), copy(media));
        }

        @Override
        public List<SurveyMedia> lockForUpdate(Collection<SurveyMediaId> ids) {
            List<SurveyMediaId> sorted = ids.stream().sorted(Comparator.comparing(SurveyMediaId::getValue)).toList();
            journal.add("media:lock:" + sorted);
            return sorted.stream().filter(stored::containsKey).map(stored::get).map(InMemoryMediaRepositories::copy)
                    .toList();
        }

        @Override
        public int deleteExpiredRecords(String checklistId, Instant finishedAtOrBefore, int limit) {
            journal.add("media:delete-expired");
            List<SurveyMediaId> ids = stored.values().stream().filter(m -> m.getChecklistId().equals(checklistId)
                    && m.getStatus() == MediaStatus.UPLOAD_EXPIRED && !m.getFinishedAt().isAfter(finishedAtOrBefore))
                    .map(SurveyMedia::getId).limit(limit).toList();
            ids.forEach(stored::remove);
            return ids.size();
        }

        @Override
        public List<MediaRef> findReadyWithoutContentMissing(String afterIdOrNull, int limit) {
            return stored.values().stream().filter(m -> m.getStatus() == MediaStatus.READY
                    && m.getContentMissingAt() == null && m.getContentDeletedAt() == null
                    && (afterIdOrNull == null || m.getId().getValue().compareTo(afterIdOrNull) > 0))
                    .sorted(Comparator.comparing(m -> m.getId().getValue())).limit(limit)
                    .map(m -> new MediaRef(m.getId(), m.getChecklistId())).toList();
        }

        @Override
        public java.util.Set<String> findQuarantineKeysInUse() {
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (SurveyMedia m : stored.values()) {
                if (m.getStatus() == MediaStatus.RECEIVING && m.getReceiveKey() != null) {
                    keys.add(m.getReceiveKey());
                }
                if (m.getStatus() == MediaStatus.PROCESSING && m.getSourceStorageKey() != null) {
                    keys.add(m.getSourceStorageKey());
                }
            }
            return keys;
        }

        @Override
        public List<PlacedMedia> findSelectedPlacements(String checklistId) {
            journal.add("media:placements");
            return stored.values().stream().filter(m -> m.getChecklistId().equals(checklistId) && m.isSelected())
                    .sorted(Comparator.comparing(m -> m.getId().getValue()))
                    .map(m -> new PlacedMedia(m.getId(), m.getPlacement().spaceIdOrNull(), m.getCategory())).toList();
        }

        @Override
        public List<SurveyMediaId> findOpenUploadIds(String checklistId, String uploaderUserId) {
            journal.add("media:open-uploads");
            return stored.values().stream().filter(m -> m.getChecklistId().equals(checklistId)
                    && m.getUploadedByUserId().equals(uploaderUserId) && (m.getStatus() == MediaStatus.AWAITING_CONTENT
                    || m.getStatus() == MediaStatus.RECEIVING)).map(SurveyMedia::getId)
                    .sorted(Comparator.comparing(SurveyMediaId::getValue)).toList();
        }

        @Override
        public List<ChecklistPerson> findUploaders(Collection<String> checklistIds) {
            return stored.values().stream().filter(photo -> checklistIds.contains(photo.getChecklistId()))
                    .map(photo -> new ChecklistPerson(photo.getChecklistId(), photo.getUploadedByUserId())).distinct()
                    .toList();
        }

        @Override
        public List<ChecklistPerson> findRemovers(Collection<String> checklistIds) {
            return stored.values().stream().filter(photo -> checklistIds.contains(photo.getChecklistId())
                            && photo.getRemoval() != null)
                    .map(photo -> new ChecklistPerson(photo.getChecklistId(), photo.getRemoval().byUserId())).distinct()
                    .toList();
        }

        @Override
        public List<SurveyMedia> findByIds(Collection<SurveyMediaId> ids) {
            return ids.stream().map(this::stored).flatMap(Optional::stream)
                    .sorted(Comparator.comparing(photo -> photo.getId().getValue())).toList();
        }

        @Override
        public List<SurveyMedia> findForChecklist(String checklistId) {
            journal.add("media:read");
            return stored.values().stream().filter(m -> m.getChecklistId().equals(checklistId))
                    .sorted(Comparator.comparingInt(SurveyMedia::getMediaNo)).map(InMemoryMediaRepositories::copy)
                    .toList();
        }

        @Override
        public Optional<SurveyMedia> findById(SurveyMediaId id) {
            journal.add("media:read:" + id.getValue());
            return stored(id);
        }

        @Override
        public Optional<String> findChecklistIdById(SurveyMediaId id) {
            journal.add("media:checklistId");
            return Optional.ofNullable(stored.get(id)).map(SurveyMedia::getChecklistId);
        }

        @Override
        public Optional<SurveyMedia> findByUploadKey(String checklistId, String uploadedByUserId, String uploadKey) {
            journal.add("media:byKey");
            return stored.values().stream().filter(m -> m.getChecklistId().equals(checklistId)
                    && m.getUploadedByUserId().equals(uploadedByUserId) && m.getUploadKey().equals(uploadKey))
                    .findFirst().map(InMemoryMediaRepositories::copy);
        }

        @Override
        public List<SurveyMedia> findCommittedHashOnOtherVisits(String leadId, String checklistId, String sha256) {
            journal.add("media:otherVisits");
            String[] own = checklistLeadAndVisit.get(checklistId);
            return stored.values().stream().filter(m -> {
                String[] of = checklistLeadAndVisit.get(m.getChecklistId());
                return of != null && own != null && of[0].equals(leadId) && !of[1].equals(own[1])
                        && m.getContentReceivedAt() != null && m.getSourceSha256().equals(sha256);
            }).map(InMemoryMediaRepositories::copy).toList();
        }

        @Override
        public boolean claimIfAwaiting(SurveyMediaId id, String token, String attemptKey, Instant now,
                                       Instant createdAfter) {
            journal.add("media:claim");
            SurveyMedia current = stored.get(id);
            if (current == null || current.getStatus() != MediaStatus.AWAITING_CONTENT || current.isRemoved()
                    || !current.getCreatedAt().isAfter(createdAfter)) {
                return false;
            }
            SurveyMedia claimed = copy(current);
            claimed.claim(token, attemptKey, now);
            claimed.markPersisted(current.getVersion() + 1);
            stored.put(id, claimed);
            return true;
        }

        @Override
        public boolean releaseClaimIfToken(SurveyMediaId id, String token) {
            journal.add("media:release");
            SurveyMedia current = stored.get(id);
            if (current == null || current.getStatus() != MediaStatus.RECEIVING
                    || !token.equals(current.getReceiveToken())) {
                return false;
            }
            SurveyMedia released = copy(current);
            released.releaseClaim();
            released.markPersisted(current.getVersion() + 1);
            stored.put(id, released);
            return true;
        }

        @Override
        public int expireStale(String checklistId, Instant createdAtOrBefore, Instant now) {
            journal.add("media:expireStale");
            int count = 0;
            for (SurveyMedia current : List.copyOf(stored.values())) {
                if (current.getChecklistId().equals(checklistId)
                        && current.getStatus() == MediaStatus.AWAITING_CONTENT
                        && !current.getCreatedAt().isAfter(createdAtOrBefore)) {
                    SurveyMedia expired = copy(current);
                    expired.expire(now);
                    expired.markPersisted(current.getVersion() + 1);
                    stored.put(current.getId(), expired);
                    count++;
                }
            }
            return count;
        }

        @Override
        public List<MediaRef> findAwaitingCreatedBefore(Instant cutoff, int limit) {
            return refs(m -> m.getStatus() == MediaStatus.AWAITING_CONTENT && !m.getCreatedAt().isAfter(cutoff),
                    limit);
        }

        @Override
        public List<MediaRef> findReceivingStartedBefore(Instant cutoff, int limit) {
            return refs(m -> m.getStatus() == MediaStatus.RECEIVING && !m.getReceiveStartedAt().isAfter(cutoff),
                    limit);
        }

        @Override
        public List<MediaRef> findProcessingReceivedBefore(Instant cutoff, int limit) {
            return refs(m -> m.getStatus() == MediaStatus.PROCESSING && !m.getContentReceivedAt().isAfter(cutoff),
                    limit);
        }

        @Override
        public List<MediaRef> findSourcesNotDeletedBefore(Instant cutoff, int limit) {
            return refs(m -> m.getSourceStorageKey() != null && m.getSourceDeletedAt() == null
                    && m.getStatus() != MediaStatus.PROCESSING && !m.getContentReceivedAt().isAfter(cutoff), limit);
        }

        @Override
        public List<MediaRef> findDeletionsRequestedBefore(Instant cutoff, int limit) {
            return refs(m -> m.getContentDeleteRequestedAt() != null && m.getContentDeletedAt() == null
                    && !m.getContentDeleteRequestedAt().isAfter(cutoff), limit);
        }

        @Override
        public List<MediaRef> findExpiredFinishedBefore(Instant cutoff, int limit) {
            return refs(m -> m.getStatus() == MediaStatus.UPLOAD_EXPIRED && !m.getFinishedAt().isAfter(cutoff),
                    limit);
        }

        private List<MediaRef> refs(Predicate<SurveyMedia> filter, int limit) {
            journal.add("media:find");
            return stored.values().stream().filter(filter).limit(limit)
                    .map(m -> new MediaRef(m.getId(), m.getChecklistId())).toList();
        }
    }

    /** Media-set rows by checklist. */
    public static class Sets implements SurveyMediaSetDomainRepository {

        private final Map<String, MediaSet> stored = new LinkedHashMap<>();
        private final List<String> journal;

        public Sets(List<String> journal) {
            this.journal = journal;
        }

        public Optional<MediaSet> stored(String checklistId) {
            return Optional.ofNullable(stored.get(checklistId)).map(Sets::copy);
        }

        @Override
        public void insertIgnoringExisting(String checklistId, Instant now) {
            journal.add("set:insert");
            stored.putIfAbsent(checklistId, MediaSet.start(checklistId, now));
        }

        @Override
        public Optional<MediaSet> lockForUpdate(String checklistId) {
            journal.add("set:lock");
            return stored(checklistId);
        }

        @Override
        public Optional<MediaSet> findForRead(String checklistId) {
            journal.add("set:read");
            return stored(checklistId);
        }

        @Override
        public void update(MediaSet set) {
            journal.add("set:update");
            stored.put(set.getChecklistId(), copy(set));
        }

        private static MediaSet copy(MediaSet set) {
            return MediaSet.load(set.getChecklistId(), set.getSetVersion(), set.getLastMediaNo(), set.getUpdatedAt());
        }
    }

    /** Variants by photo, insert-only. */
    public static class Variants implements SurveyMediaVariantDomainRepository {

        private final Map<SurveyMediaId, List<MediaVariant>> stored = new LinkedHashMap<>();

        @Override
        public java.util.Set<String> findStoredKeys(Collection<String> keys) {
            java.util.Set<String> found = new java.util.HashSet<>();
            stored.values().forEach(list -> list.forEach(variant -> {
                if (keys.contains(variant.storageKey())) {
                    found.add(variant.storageKey());
                }
            }));
            return found;
        }

        @Override
        public void saveAll(SurveyMediaId mediaId, List<MediaVariant> variants) {
            if (stored.containsKey(mediaId)) {
                throw new IllegalStateException("variants are insert-only");
            }
            stored.put(mediaId, List.copyOf(variants));
        }

        @Override
        public Map<SurveyMediaId, List<MediaVariant>> findByMediaIds(Collection<SurveyMediaId> mediaIds) {
            Map<SurveyMediaId, List<MediaVariant>> found = new LinkedHashMap<>();
            for (SurveyMediaId id : mediaIds) {
                if (stored.containsKey(id)) {
                    found.put(id, stored.get(id));
                }
            }
            return found;
        }
    }

    /** Jobs by photo and kind; claims and fenced changes behave like the SQL of spec 4.4 step 3. */
    public static class Jobs implements SurveyMediaJobDomainRepository {

        private final Map<String, MediaJob> stored = new LinkedHashMap<>();
        private final List<String> journal;
        private long nextId = 1;
        /** The uploader of each photo, for the claim. */
        public final Map<SurveyMediaId, String> uploaders = new HashMap<>();
        /** Processing milliseconds per uploader, for the fair claim (spec A-27). */
        public final Map<String, Long> processingMillis = new HashMap<>();

        public Jobs(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public void upsertProcess(SurveyMediaId mediaId, Instant dueAt, Instant now) {
            upsert(mediaId, MediaJobKind.PROCESS, dueAt, now);
        }

        @Override
        public void upsertDeleteContent(SurveyMediaId mediaId, Instant dueAt, Instant now) {
            upsert(mediaId, MediaJobKind.DELETE_CONTENT, dueAt, now);
        }

        @Override
        public List<MediaJob> findByMedia(SurveyMediaId mediaId) {
            return stored.values().stream().filter(job -> job.mediaId().equals(mediaId)).toList();
        }

        public MediaJob get(long id) {
            return stored.values().stream().filter(job -> job.id() == id).findFirst().orElseThrow();
        }

        @Override
        public Optional<MediaJobClaim> claimNextProcess(Instant now, Instant hourStart,
                                                        Collection<String> excludedUploaders, String token,
                                                        Instant leaseUntil) {
            journal.add("job:claim");
            Optional<MediaJob> next = stored.values().stream()
                    .filter(job -> job.kind() == MediaJobKind.PROCESS && job.state() == MediaJobState.PENDING
                            && !job.dueAt().isAfter(now))
                    .filter(job -> !excludedUploaders.contains(uploaders.get(job.mediaId())))
                    .min(Comparator.<MediaJob>comparingLong(job -> processingMillis.getOrDefault(
                            uploaders.get(job.mediaId()), 0L)).thenComparing(MediaJob::dueAt)
                            .thenComparingLong(MediaJob::id));
            if (next.isEmpty()) {
                return Optional.empty();
            }
            MediaJob job = next.get();
            MediaJob claimed = new MediaJob(job.id(), job.mediaId(), job.kind(), job.state(), job.attempts() + 1,
                    job.limitHits(), token, leaseUntil, job.lastErrorCodeOrNull(), job.createdAt(), now);
            put(claimed);
            return Optional.of(new MediaJobClaim(claimed, uploaders.get(job.mediaId())));
        }

        @Override
        public void closeProcess(SurveyMediaId mediaId, MediaJobState state, String code, Instant now) {
            journal.add("job:close:" + state);
            stored.values().stream().filter(job -> job.mediaId().equals(mediaId) && job.kind() == MediaJobKind.PROCESS
                    && job.state() == MediaJobState.PENDING).toList().forEach(job -> put(new MediaJob(job.id(),
                    job.mediaId(), job.kind(), state, job.attempts(), job.limitHits(), null, job.dueAt(), code,
                    job.createdAt(), now)));
        }

        @Override
        public boolean anyDue(MediaJobKind kind, Instant now) {
            return stored.values().stream().anyMatch(job -> job.kind() == kind
                    && job.state() == MediaJobState.PENDING && !job.dueAt().isAfter(now));
        }

        @Override
        public Optional<MediaJob> claimNextDeleteContent(Instant now, String token, Instant leaseUntil) {
            journal.add("job:claim-delete");
            Optional<MediaJob> next = stored.values().stream()
                    .filter(job -> job.kind() == MediaJobKind.DELETE_CONTENT && job.state() == MediaJobState.PENDING
                            && !job.dueAt().isAfter(now))
                    .min(Comparator.comparing(MediaJob::dueAt).thenComparingLong(MediaJob::id));
            if (next.isEmpty()) {
                return Optional.empty();
            }
            MediaJob job = next.get();
            MediaJob claimed = new MediaJob(job.id(), job.mediaId(), job.kind(), job.state(), job.attempts() + 1,
                    job.limitHits(), token, leaseUntil, job.lastErrorCodeOrNull(), job.createdAt(), now);
            put(claimed);
            return Optional.of(claimed);
        }

        @Override
        public boolean renewLease(long jobId, String token, Instant leaseUntil, Instant now) {
            MediaJob job = get(jobId);
            if (!holds(job, token)) {
                return false;
            }
            put(new MediaJob(job.id(), job.mediaId(), job.kind(), job.state(), job.attempts(), job.limitHits(),
                    token, leaseUntil, job.lastErrorCodeOrNull(), job.createdAt(), now));
            return true;
        }

        @Override
        public Optional<MediaJob> lockForUpdate(long jobId) {
            journal.add("job:lock");
            return stored.values().stream().filter(job -> job.id() == jobId).findFirst();
        }

        @Override
        public boolean finish(long jobId, String token, MediaJobState state, String errorCodeOrNull, Instant now) {
            MediaJob job = get(jobId);
            if (!holds(job, token)) {
                return false;
            }
            journal.add("job:finish:" + state);
            put(new MediaJob(job.id(), job.mediaId(), job.kind(), state, job.attempts(), job.limitHits(), null,
                    job.dueAt(), errorCodeOrNull, job.createdAt(), now));
            return true;
        }

        @Override
        public boolean retry(long jobId, String token, Instant dueAt, String errorCode, int limitHits, Instant now) {
            MediaJob job = get(jobId);
            if (!holds(job, token)) {
                return false;
            }
            journal.add("job:retry:" + errorCode);
            put(new MediaJob(job.id(), job.mediaId(), job.kind(), MediaJobState.PENDING, job.attempts(), limitHits,
                    null, dueAt, errorCode, job.createdAt(), now));
            return true;
        }

        @Override
        public boolean defer(long jobId, String token, Instant dueAt, Instant now) {
            MediaJob job = get(jobId);
            if (!holds(job, token)) {
                return false;
            }
            journal.add("job:defer");
            put(new MediaJob(job.id(), job.mediaId(), job.kind(), MediaJobState.PENDING, job.attempts() - 1,
                    job.limitHits(), null, dueAt, job.lastErrorCodeOrNull(), job.createdAt(), now));
            return true;
        }

        /** A test puts the lease of a job aside, as another worker that claimed it after the lease ran out. */
        public void stealLease(long jobId, String otherToken) {
            MediaJob job = get(jobId);
            put(new MediaJob(job.id(), job.mediaId(), job.kind(), job.state(), job.attempts() + 1, job.limitHits(),
                    otherToken, job.dueAt(), job.lastErrorCodeOrNull(), job.createdAt(), job.updatedAt()));
        }

        private static boolean holds(MediaJob job, String token) {
            return job.state() == MediaJobState.PENDING && token.equals(job.leaseTokenOrNull());
        }

        private void put(MediaJob job) {
            stored.put(job.mediaId().getValue() + "/" + job.kind(), job);
        }

        private void upsert(SurveyMediaId mediaId, MediaJobKind kind, Instant dueAt, Instant now) {
            journal.add("job:upsert:" + kind);
            String key = mediaId.getValue() + "/" + kind;
            MediaJob current = stored.get(key);
            stored.put(key, new MediaJob(current == null ? nextId++ : current.id(), mediaId, kind,
                    MediaJobState.PENDING, 0, 0, null, dueAt, null, current == null ? now : current.createdAt(), now));
        }
    }

    /** Body slots by id. */
    public static class BodySlots implements SurveyMediaBodySlotDomainRepository {

        private final Map<Long, Object[]> stored = new LinkedHashMap<>();
        private final List<String> journal;
        private long nextId = 1;

        public BodySlots(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public long take(String userId, String instanceId, Instant now, Instant expiresAt) {
            journal.add("slot:take");
            long id = nextId++;
            stored.put(id, new Object[] {userId, instanceId, expiresAt});
            return id;
        }

        @Override
        public void free(long slotId) {
            journal.add("slot:free");
            stored.remove(slotId);
        }

        @Override
        public int countLive(String userId, Instant now) {
            return (int) stored.values().stream().filter(row -> row[0].equals(userId)
                    && ((Instant) row[2]).isAfter(now)).count();
        }

        @Override
        public int deleteExpired(Instant now) {
            journal.add("slot:purge");
            int before = stored.size();
            stored.values().removeIf(row -> !((Instant) row[2]).isAfter(now));
            return before - stored.size();
        }
    }

    /** Counters, locks and grouping checks in memory; the photos give the hourly usage. */
    public static class Counters implements MediaCountersRepository {

        private final Map<String, long[]> counters = new HashMap<>();
        private final Map<String, Instant> exceeded = new HashMap<>();
        private final List<String> journal;
        private final Media media;
        public final List<Object[]> listRows = new ArrayList<>();
        /** The stored access-log rows the MEDIA_LIST grouping looks at (spec A-6); none unless a test wires them. */
        public Supplier<List<SurveyAccessLog>> accessLogSource = List::of;
        /** The stored security events the throttle looks at (spec A-19); none unless a test wires them. */
        public Supplier<List<SurveySecurityEvent>> eventSource = List::of;

        public Counters(List<String> journal, Media media) {
            this.journal = journal;
            this.media = media;
        }

        @Override
        public void lockUser(String userId) {
            journal.add("user:lock");
        }

        @Override
        public int deleteBudgetsBefore(Instant hourStartBefore) {
            journal.add("budget:purge");
            return 0;
        }

        @Override
        public HourlyUsage hourlyUsage(String userId, Instant since) {
            journal.add("user:usage");
            List<SurveyMedia> recent = media.all().stream().filter(m -> m.getUploadedByUserId().equals(userId)
                    && m.getCreatedAt().isAfter(since)).toList();
            return new HourlyUsage(recent.size(), recent.stream().mapToLong(SurveyMedia::getDeclaredSizeBytes).sum(),
                    recent.stream().map(SurveyMedia::getCreatedAt).min(Comparator.naturalOrder()).orElse(null));
        }

        @Override
        public long counterValue(String userId, Instant hourStart, MediaCounterKind kind) {
            long[] value = counters.get(key(userId, hourStart, kind));
            return value == null ? 0 : value[0];
        }

        @Override
        public OptionalLong countIfBelow(String userId, Instant hourStart, MediaCounterKind kind,
                                         long effectiveLimit) {
            journal.add("budget:" + kind);
            long[] value = counters.computeIfAbsent(key(userId, hourStart, kind), k -> new long[1]);
            if (value[0] != 0 && value[0] >= effectiveLimit) {
                return OptionalLong.empty();
            }
            value[0]++;
            return OptionalLong.of(value[0]);
        }

        @Override
        public boolean markExceededFirst(String userId, Instant hourStart, MediaCounterKind kind, Instant now) {
            return exceeded.putIfAbsent(key(userId, hourStart, kind), now) == null;
        }

        @Override
        public void add(String userId, Instant hourStart, MediaCounterKind kind, long amount) {
            counters.computeIfAbsent(key(userId, hourStart, kind), k -> new long[1])[0] += amount;
        }

        @Override
        public boolean hasRecentMediaListRow(String userId, String checklistId, Instant since) {
            return listRows.stream().anyMatch(row -> row[0].equals(userId) && row[1].equals(checklistId)
                    && ((Instant) row[2]).isAfter(since))
                    || accessLogSource.get().stream().anyMatch(row -> row.userId().equals(userId)
                    && row.resourceTypeOrNull() == AccessResourceType.MEDIA_LIST
                    && checklistId.equals(row.resourceIdOrNull()) && row.accessedAt().isAfter(since));
        }

        @Override
        public boolean tryLockEventKey(String userId, String resourceId, SecurityAction action,
                                       SecurityOutcome outcome) {
            journal.add("event:lock");
            return true;
        }

        @Override
        public boolean hasRecentEvent(String userId, String resourceId, SecurityAction action,
                                      SecurityOutcome outcome, Instant since) {
            return eventSource.get().stream().anyMatch(event -> event.userId().equals(userId)
                    && event.resourceId().equals(resourceId) && event.action() == action
                    && event.outcome() == outcome && event.occurredAt().isAfter(since));
        }

        private static String key(String userId, Instant hourStart, MediaCounterKind kind) {
            return userId + "|" + hourStart + "|" + kind;
        }
    }
}
