package vn.sunvibe.survey.core.domain.model.media;

import vn.sunvibe.survey.core.domain.exception.media.IllegalMediaTransitionException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate - SurveyMedia (survey-media spec 4.1, 4.2): one uploaded file of one visit, uploaded into one checklist,
 * with its processed variants. Placement, category, caption, uploader and declared hash never change (I-1, A-8). The
 * status is system-driven: AWAITING_CONTENT, RECEIVING (a content upload holds the claim), PROCESSING (bytes
 * committed, BR-17), then one final status. Removal and the ADMIN hold only add marks (spec 4.7, A-23); content
 * deletion is requested exactly for a removed READY photo that is not held. Every refused move throws
 * {@link IllegalMediaTransitionException}; the use cases check their answers before calling. Each move is one row
 * write, as the table's CHECKs are evaluated per row.
 */
public final class SurveyMedia {

    /** spec A-5: an upload not claimed within 60 minutes of creation expires. */
    public static final Duration CLAIM_WINDOW = Duration.ofMinutes(60);

    private static final Set<VariantKind> ALL_VARIANTS = EnumSet.allOf(VariantKind.class);

    private final SurveyMediaId id;
    private final String checklistId;
    private final String uploadKey;
    private final int mediaNo;
    private final MediaPlacement placement;
    private final EvidenceCategory category;
    private final String caption;
    private final String uploadedByUserId;
    private final Instant createdAt;
    private final long declaredSizeBytes;
    private final String sourceSha256;
    private MediaStatus status;
    private MediaStatusReason statusReason;
    private String receiveToken;
    private String receiveKey;
    private Instant receiveStartedAt;
    private Instant contentReceivedAt;
    private String sourceStorageKey;
    private Instant sourceDeletedAt;
    private SourceFormat sourceFormat;
    private Instant readyAt;
    private Instant finishedAt;
    private MediaRemoval removal;
    private MediaHold hold;
    private Instant contentDeleteRequestedAt;
    private Instant contentDeletedAt;
    private Instant contentMissingAt;
    private List<MediaVariant> variants;
    private Long version;

    private SurveyMedia(SurveyMediaId id, String checklistId, String uploadKey, int mediaNo, MediaPlacement placement,
                        EvidenceCategory category, String caption, String uploadedByUserId, Instant createdAt,
                        long declaredSizeBytes, String sourceSha256, MediaStatus status,
                        MediaStatusReason statusReason, String receiveToken, String receiveKey,
                        Instant receiveStartedAt, Instant contentReceivedAt, String sourceStorageKey,
                        Instant sourceDeletedAt, SourceFormat sourceFormat, Instant readyAt, Instant finishedAt,
                        MediaRemoval removal, MediaHold hold, Instant contentDeleteRequestedAt,
                        Instant contentDeletedAt, Instant contentMissingAt, List<MediaVariant> variants,
                        Long version) {
        if (id == null || checklistId == null || uploadKey == null || placement == null || category == null
                || uploadedByUserId == null || createdAt == null || sourceSha256 == null || status == null
                || hold == null || variants == null) {
            throw new IllegalArgumentException("A survey photo needs its ids, placement, category, uploader, time, "
                    + "hash, status and hold.");
        }
        if (mediaNo < 1 || declaredSizeBytes < 1) {
            throw new IllegalArgumentException("Media number and declared size are positive.");
        }
        this.id = id;
        this.checklistId = checklistId;
        this.uploadKey = uploadKey;
        this.mediaNo = mediaNo;
        this.placement = placement;
        this.category = category;
        this.caption = caption;
        this.uploadedByUserId = uploadedByUserId;
        this.createdAt = createdAt;
        this.declaredSizeBytes = declaredSizeBytes;
        this.sourceSha256 = sourceSha256;
        this.status = status;
        this.statusReason = statusReason;
        this.receiveToken = receiveToken;
        this.receiveKey = receiveKey;
        this.receiveStartedAt = receiveStartedAt;
        this.contentReceivedAt = contentReceivedAt;
        this.sourceStorageKey = sourceStorageKey;
        this.sourceDeletedAt = sourceDeletedAt;
        this.sourceFormat = sourceFormat;
        this.readyAt = readyAt;
        this.finishedAt = finishedAt;
        this.removal = removal;
        this.hold = hold;
        this.contentDeleteRequestedAt = contentDeleteRequestedAt;
        this.contentDeletedAt = contentDeletedAt;
        this.contentMissingAt = contentMissingAt;
        this.variants = List.copyOf(variants);
        this.version = version;
    }

    /** spec 4.4 step 1: a new photo awaiting its content, created by the holder with its declaration. */
    public static SurveyMedia create(SurveyMediaId id, String checklistId, String uploadKey, int mediaNo,
                                     MediaPlacement placement, EvidenceCategory category, String captionOrNull,
                                     String uploadedByUserId, long declaredSizeBytes, String sourceSha256,
                                     Instant now) {
        return new SurveyMedia(id, checklistId, uploadKey, mediaNo, placement, category, captionOrNull,
                uploadedByUserId, now, declaredSizeBytes, sourceSha256, MediaStatus.AWAITING_CONTENT, null, null,
                null, null, null, null, null, null, null, null, null, MediaHold.NONE, null, null, null, List.of(),
                null);
    }

    /** Load from database (reconstruct). */
    public static SurveyMedia load(SurveyMediaId id, String checklistId, String uploadKey, int mediaNo,
                                   MediaPlacement placement, EvidenceCategory category, String caption,
                                   String uploadedByUserId, Instant createdAt, long declaredSizeBytes,
                                   String sourceSha256, MediaStatus status, MediaStatusReason statusReason,
                                   String receiveToken, String receiveKey, Instant receiveStartedAt,
                                   Instant contentReceivedAt, String sourceStorageKey, Instant sourceDeletedAt,
                                   SourceFormat sourceFormat, Instant readyAt, Instant finishedAt,
                                   MediaRemoval removal, MediaHold hold, Instant contentDeleteRequestedAt,
                                   Instant contentDeletedAt, Instant contentMissingAt, List<MediaVariant> variants,
                                   Long version) {
        return new SurveyMedia(id, checklistId, uploadKey, mediaNo, placement, category, caption, uploadedByUserId,
                createdAt, declaredSizeBytes, sourceSha256, status, statusReason, receiveToken, receiveKey,
                receiveStartedAt, contentReceivedAt, sourceStorageKey, sourceDeletedAt, sourceFormat, readyAt,
                finishedAt, removal, hold, contentDeleteRequestedAt, contentDeletedAt, contentMissingAt, variants,
                version);
    }

    // ==================== upload and processing (spec 4.2, 4.4) ====================

    /** spec 4.4 step 2: a content upload claims a photo awaiting content, not removed, within 60 minutes. */
    public void claim(String token, String attemptKey, Instant now) {
        if (status != MediaStatus.AWAITING_CONTENT || isRemoved() || !now.isBefore(createdAt.plus(CLAIM_WINDOW))) {
            throw refused("claim");
        }
        status = MediaStatus.RECEIVING;
        receiveToken = token;
        receiveKey = attemptKey;
        receiveStartedAt = now;
    }

    /** spec 4.4 step 2: an aborted, failed or stale upload gives the claim back. */
    public void releaseClaim() {
        if (status != MediaStatus.RECEIVING) {
            throw refused("release its claim");
        }
        status = MediaStatus.AWAITING_CONTENT;
        clearClaim();
    }

    /**
     * spec 4.4 step 2, BR-17: the completion point. The checked bytes of the declared size are stored under the
     * attempt's key; the photo is PROCESSING from here, whatever happens to the lead later.
     */
    public void commit(String sourceKey, long sizeBytes, Instant now) {
        if (status != MediaStatus.RECEIVING || isRemoved() || sizeBytes != declaredSizeBytes || sourceKey == null) {
            throw refused("commit its content");
        }
        status = MediaStatus.PROCESSING;
        clearClaim();
        sourceStorageKey = sourceKey;
        contentReceivedAt = now;
    }

    /** spec 4.4 step 3: the worker's outcome with exactly one variant of each kind; never for a removed photo. */
    public void ready(List<MediaVariant> newVariants, SourceFormat format, Instant now) {
        if (status != MediaStatus.PROCESSING || isRemoved()) {
            throw refused("become ready");
        }
        if (format == null || newVariants.size() != ALL_VARIANTS.size()
                || !EnumSet.copyOf(newVariants.stream().map(MediaVariant::kind).toList()).equals(ALL_VARIANTS)) {
            throw new IllegalArgumentException("A ready photo has one FULL, one PREVIEW and one THUMBNAIL variant.");
        }
        status = MediaStatus.READY;
        sourceFormat = format;
        readyAt = now;
        finishedAt = now;
        variants = newVariants.stream().sorted((a, b) -> a.kind().compareTo(b.kind())).toList();
    }

    /** spec 4.5: a content reason ends the photo REJECTED; a removed photo is cancelled instead. */
    public void reject(MediaStatusReason reason, Instant now) {
        if (reason == null || !reason.isRejection()) {
            throw new IllegalArgumentException("A rejection has one of the eight content reasons.");
        }
        if (status != MediaStatus.PROCESSING || isRemoved()) {
            throw refused("be rejected");
        }
        finish(MediaStatus.REJECTED, reason, now);
    }

    /** spec A-7, BR-18: processing could not complete; a removed photo is cancelled instead. */
    public void fail(Instant now) {
        if (status != MediaStatus.PROCESSING || isRemoved()) {
            throw refused("fail");
        }
        finish(MediaStatus.FAILED, MediaStatusReason.PROCESSING_UNAVAILABLE, now);
    }

    /** spec 4.7, BR-17: a photo removed while PROCESSING ends CANCELLED, never READY. */
    public void cancel(Instant now) {
        if (status != MediaStatus.PROCESSING || !isRemoved()) {
            throw refused("be cancelled");
        }
        finish(MediaStatus.CANCELLED, MediaStatusReason.REMOVED, now);
    }

    /** spec 4.2, 4.8: no content arrived in time, or a takeover ended the previous holder's upload. */
    public void expire(Instant now) {
        if (status != MediaStatus.AWAITING_CONTENT && status != MediaStatus.RECEIVING) {
            throw refused("expire");
        }
        clearClaim();
        finish(MediaStatus.UPLOAD_EXPIRED, MediaStatusReason.CONTENT_NOT_RECEIVED, now);
    }

    /** spec 4.4 step 3: the raw source is gone from the store (set only once its delete succeeded). */
    public void markSourceDeleted(Instant now) {
        if (sourceStorageKey == null || sourceDeletedAt != null || status == MediaStatus.RECEIVING) {
            throw refused("mark its source deleted");
        }
        sourceDeletedAt = now;
    }

    // ==================== removal and deletion (spec 4.7, A-9, A-17) ====================

    /**
     * spec 4.7, A-9: the sticky removal mark, by the holder or by a G1 save's cascade. A READY photo that is not held
     * gets its content deletion request at once; a held one at its release (A-23). The status does not change: a
     * PROCESSING photo is cancelled at its outcome.
     */
    public void remove(RemovalKind kind, String byUserId, MediaRemovalCorrection correctionOrNull, Long saveIdOrNull,
                       Instant now) {
        MediaRemoval mark = new MediaRemoval(now, byUserId, kind, correctionOrNull, saveIdOrNull);
        if (isRemoved()) {
            throw refused("be removed again");
        }
        removal = mark;
        if (status == MediaStatus.READY && !isHeld()) {
            contentDeleteRequestedAt = now;
        }
    }

    /** spec 4.7: the deletion request of a removed READY photo that is not held and has none yet. */
    public void requestContentDeletion(Instant now) {
        if (!isRemoved() || status != MediaStatus.READY || isHeld() || contentDeleteRequestedAt != null) {
            throw refused("request its content deletion");
        }
        contentDeleteRequestedAt = now;
    }

    /** spec 4.7, I-4: the deletion job confirmed that no object of the photo is left. */
    public void markContentDeleted(Instant now) {
        if (contentDeleteRequestedAt == null || contentDeletedAt != null || isHeld()) {
            throw refused("mark its content deleted");
        }
        contentDeletedAt = now;
    }

    /** spec 5.1: a reconciliation after a database restore found a READY photo's variants missing. */
    public void markContentMissing(Instant now) {
        if (status != MediaStatus.READY || contentMissingAt != null) {
            throw refused("mark its content missing");
        }
        contentMissingAt = now;
    }

    // ==================== security hold (spec A-23) ====================

    /**
     * spec A-23: an ADMIN holds a PROCESSING or READY photo that is not removed.
     *
     * @return false for a photo already held: a no-op that keeps the first reason
     */
    public boolean hold(String byUserId, HoldReason reason, Instant now) {
        if (isHeld()) {
            return false;
        }
        if ((status != MediaStatus.PROCESSING && status != MediaStatus.READY) || isRemoved()) {
            throw refused("be held");
        }
        hold = hold.hold(byUserId, reason, now);
        return true;
    }

    /**
     * spec A-23: an ADMIN releases the hold; a removed READY photo then gets its deletion request in the same write.
     *
     * @return false for a photo not held: a no-op
     */
    public boolean release(String byUserId, Instant now) {
        if (!isHeld()) {
            return false;
        }
        hold = hold.release(byUserId, now);
        if (isRemoved() && status == MediaStatus.READY && contentDeleteRequestedAt == null
                && contentDeletedAt == null) {
            contentDeleteRequestedAt = now;
        }
        return true;
    }

    // ==================== derived states (spec 4.2) ====================

    public boolean isRemoved() {
        return removal != null;
    }

    public boolean isHeld() {
        return hold.isHeld();
    }

    /** Counts for the cap of 100 (BR-6): uploading, processing or ready, and not removed. */
    public boolean isSelected() {
        return !isRemoved() && (status == MediaStatus.AWAITING_CONTENT || status == MediaStatus.RECEIVING
                || status == MediaStatus.PROCESSING || status == MediaStatus.READY);
    }

    /** Counts for the 20 pending: selected and not READY. */
    public boolean isPending() {
        return isSelected() && status != MediaStatus.READY;
    }

    public boolean isContentPresent() {
        return contentDeletedAt == null && contentMissingAt == null;
    }

    /** READY, content present, not removed, not held (BR-1, BR-8, A-23); also usable for coverage. */
    public boolean isServable() {
        return status == MediaStatus.READY && isContentPresent() && !isRemoved() && !isHeld();
    }

    /** spec A-23: an ADMIN also reads a held READY photo whose content is present, removed or not. */
    public boolean isServableToAdmin() {
        return status == MediaStatus.READY && isContentPresent() && (isHeld() || !isRemoved());
    }

    // ==================== internals ====================

    private void finish(MediaStatus finalStatus, MediaStatusReason reason, Instant now) {
        status = finalStatus;
        statusReason = reason;
        finishedAt = now;
    }

    private void clearClaim() {
        receiveToken = null;
        receiveKey = null;
        receiveStartedAt = null;
    }

    private IllegalMediaTransitionException refused(String move) {
        return new IllegalMediaTransitionException(move, id.getValue(),
                status + (isRemoved() ? ", removed" : "") + (isHeld() ? ", held" : ""));
    }

    // ==================== getters ====================

    public SurveyMediaId getId() {
        return id;
    }

    public String getChecklistId() {
        return checklistId;
    }

    public String getUploadKey() {
        return uploadKey;
    }

    public int getMediaNo() {
        return mediaNo;
    }

    public MediaPlacement getPlacement() {
        return placement;
    }

    public EvidenceCategory getCategory() {
        return category;
    }

    public String getCaption() {
        return caption;
    }

    public String getUploadedByUserId() {
        return uploadedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getDeclaredSizeBytes() {
        return declaredSizeBytes;
    }

    public String getSourceSha256() {
        return sourceSha256;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public MediaStatusReason getStatusReason() {
        return statusReason;
    }

    public String getReceiveToken() {
        return receiveToken;
    }

    public String getReceiveKey() {
        return receiveKey;
    }

    public Instant getReceiveStartedAt() {
        return receiveStartedAt;
    }

    public Instant getContentReceivedAt() {
        return contentReceivedAt;
    }

    public String getSourceStorageKey() {
        return sourceStorageKey;
    }

    public Instant getSourceDeletedAt() {
        return sourceDeletedAt;
    }

    public SourceFormat getSourceFormat() {
        return sourceFormat;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /** The removal mark, or null while not removed. */
    public MediaRemoval getRemoval() {
        return removal;
    }

    public MediaHold getHold() {
        return hold;
    }

    public Instant getContentDeleteRequestedAt() {
        return contentDeleteRequestedAt;
    }

    public Instant getContentDeletedAt() {
        return contentDeletedAt;
    }

    public Instant getContentMissingAt() {
        return contentMissingAt;
    }

    public List<MediaVariant> getVariants() {
        return variants;
    }

    public Long getVersion() {
        return version;
    }

    /** Called by persistence after an insert or update with the stored row's version. */
    public void markPersisted(long storedVersion) {
        this.version = storedVersion;
    }

    /** Ids and state only: never the caption, hash, keys or upload key (spec 4.9). */
    @Override
    public String toString() {
        return "SurveyMedia[" + id + " of " + checklistId + ", " + status + (isRemoved() ? ", removed" : "")
                + (isHeld() ? ", held" : "") + "]";
    }
}
