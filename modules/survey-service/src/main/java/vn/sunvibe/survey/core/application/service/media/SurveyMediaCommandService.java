package vn.sunvibe.survey.core.application.service.media;

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
import vn.sunvibe.survey.core.application.command.media.CreateMediaCommand;
import vn.sunvibe.survey.core.application.command.media.DeselectMediaCommand;
import vn.sunvibe.survey.core.application.command.media.RemoveMediaCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaIntakePausedException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaReferencedException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadKeyReusedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadStateException;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.query.media.MediaCreateResult;
import vn.sunvibe.survey.core.application.query.media.MediaStateResult;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.media.IntakeGate;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaContentHeaders.ContentHeaders;
import vn.sunvibe.survey.core.application.shared.media.MediaInputs;
import vn.sunvibe.survey.core.application.shared.media.MediaInputs.CreateInput;
import vn.sunvibe.survey.core.application.shared.media.MediaItemAssembler;
import vn.sunvibe.survey.core.application.shared.media.MediaLimitCounter;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks.LockedSets;
import vn.sunvibe.survey.core.application.shared.checklist.RevisionPhotos;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort;
import vn.sunvibe.survey.core.application.usecase.media.SurveyMediaCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.MediaDuplicateContentException;
import vn.sunvibe.survey.core.domain.exception.MediaUploadKeyTakenException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;
import vn.sunvibe.survey.core.domain.policy.media.MediaContentIdentity;
import vn.sunvibe.survey.core.domain.policy.media.MediaRemovalPolicy;
import vn.sunvibe.survey.core.domain.policy.media.PlacementRules;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSetDomainRepository;

/**
 * Survey photo commands (survey-media spec 4.4, 5.4). Each command runs in one READ COMMITTED transaction with the
 * locks of spec 5.4 in their global order; the transactions are programmatic because a refusal has work to do after
 * its rollback: a stale sweep is recorded as a pause (A-18), and a unique violation at insert is answered from a fresh
 * transaction (the replay, or the matching photo of the A-16 backstop), never a 500.
 */
@Service
public class SurveyMediaCommandService implements SurveyMediaCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SurveyMediaCommandService.class);

    private final ChecklistContextLoader loader;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaJobDomainRepository jobs;
    private final SurveyMediaSelectionDomainRepository selections;
    private final RevisionPhotos revisionPhotos;
    private final MediaSetLocks setLocks;
    private final SurveyLeadPort leads;
    private final EvidenceReferencePort references;
    private final SecurityEventRecorder events;
    private final MediaLimitCounter limits;
    private final IntakeGate intakeGate;
    private final MediaSwitches switches;
    private final MediaItemAssembler items;
    private final MediaClock clock;
    private final IdGenerator<String> ulids;
    private final TransactionTemplate commandTransaction;
    private final TransactionTemplate answerTransaction;

    public SurveyMediaCommandService(ChecklistContextLoader loader, SurveyChecklistGroupDomainRepository groups,
                                     SurveyMediaDomainRepository media, SurveyMediaSetDomainRepository sets,
                                     SurveyMediaJobDomainRepository jobs,
                                     SurveyMediaSelectionDomainRepository selections, RevisionPhotos revisionPhotos,
                                     MediaSetLocks setLocks, SurveyLeadPort leads,
                                     EvidenceReferencePort references, SecurityEventRecorder events,
                                     MediaLimitCounter limits, IntakeGate intakeGate, MediaSwitches switches,
                                     MediaItemAssembler items, MediaClock clock,
                                     @Qualifier("Ulid") IdGenerator<String> ulids,
                                     PlatformTransactionManager transactionManager) {
        this.loader = loader;
        this.groups = groups;
        this.media = media;
        this.sets = sets;
        this.jobs = jobs;
        this.selections = selections;
        this.revisionPhotos = revisionPhotos;
        this.setLocks = setLocks;
        this.leads = leads;
        this.references = references;
        this.events = events;
        this.limits = limits;
        this.intakeGate = intakeGate;
        this.switches = switches;
        this.items = items;
        this.clock = clock;
        this.ulids = ulids;
        this.commandTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction.setReadOnly(true);
    }

    /**
     * Create order (spec 5.4): (1) body, before any lookup → (2) 404 checklist or lead not visible → (3) 403 not the
     * assignee → (4) 409 read-only → (5) 422 ineligible → (6) 409 takeover required → (7) replay → (8) 409 uploads off
     * → (9) 503 intake paused → (10) 400 placement against the current G1 → (11) 429 hourly records, bytes and
     * expensive outcomes → (12) 409 content identity (A-16) → (13) 409 selected or pending limit. Locks: lead and
     * checklist shared, the G1 row shared, the user's advisory lock, the set row; the replay is checked again after
     * the advisory and set locks, since a twin may have committed meanwhile.
     */
    @Override
    public MediaCreateResult create(SurveyViewer viewer, boolean viewerCanHold, CreateMediaCommand command) {
        CreateInput input = MediaInputs.create(command);
        try {
            return inCommand(() -> createInTransaction(viewer, viewerCanHold, input));
        } catch (MediaIntakePausedException e) {
            intakeGate.recordRefusal(e);
            throw e;
        } catch (MediaUploadKeyTakenException e) {
            // spec 4.4 step 1: a twin with this key committed between the re-check and the insert: its replay
            return answerTransaction.execute(status -> replay(viewer, viewerCanHold, input, null)
                    .orElseThrow(() -> new IllegalStateException("The photo of the upload key is gone.", e)));
        } catch (MediaDuplicateContentException e) {
            // spec A-16 (a): the partial unique index refused a second selected photo with the hash
            String matching = answerTransaction.execute(status -> selectedWithHash(input));
            throw new MediaContentIdentityException(IdentityVerdict.Kind.DUPLICATE_CONTENT, matching);
        }
    }

    private MediaCreateResult createInTransaction(SurveyViewer viewer, boolean viewerCanHold, CreateInput input) {
        Instant now = clock.now();
        CommandContext context = loader.loadForMediaCommand(viewer, input.checklistId(),
                SecurityResourceType.MEDIA_SET, input.checklistId(), SecurityAction.UPLOAD);
        SurveyChecklist checklist = context.checklist();
        String checklistId = checklist.getId().getValue();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        Optional<MediaCreateResult> replay = replay(viewer, viewerCanHold, input, null);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (!switches.uploadsOn()) {
            throw new MediaUploadDisabledException();
        }
        intakeGate.requireOpen();
        IdentificationContent g1 = (IdentificationContent) groups.lockForShare(checklist.getId(),
                ChecklistGroup.IDENTIFICATION).document().content();
        MediaPlacement placement = placement(input, g1);

        limits.lockUser(viewer.userId());
        sets.insertIgnoringExisting(checklistId, now);
        MediaSet set = sets.lockForUpdate(checklistId)
                .orElseThrow(() -> new IllegalStateException("The media set row was just inserted."));
        replay = replay(viewer, viewerCanHold, input, set);
        if (replay.isPresent()) {
            return replay.get();
        }
        limits.requireHourlyRoom(viewer.userId(), input.sizeBytes(), now);
        // checklist submission spec 4.6: the caps and the identity check count own plus carried photos
        List<SurveyMedia> ofChecklist = revisionPhotos.of(checklistId);
        IdentityVerdict verdict = MediaContentIdentity.check(input.sha256(), null, ofChecklist,
                media.findCommittedHashOnOtherVisits(context.lead().leadId(), checklistId, input.sha256()));
        if (verdict.kind() != IdentityVerdict.Kind.FREE) {
            throw new MediaContentIdentityException(verdict.kind(), verdict.matchingMediaIdOrNull());
        }
        MediaLimitCounter.requireSelectionRoom(ofChecklist);

        SurveyMedia photo = SurveyMedia.create(SurveyMediaId.of(ulids.generateId()), checklistId, input.uploadKey(),
                set.nextMediaNo(), placement, input.category(), input.captionOrNull(), viewer.userId(),
                input.sizeBytes(), input.sha256(), now);
        media.insert(photo);
        set.bump(now);
        sets.update(set);
        return new MediaCreateResult(items.assemble(photo, viewerCanHold), set.getSetVersion(), true);
    }

    // ==================== the content upload (spec 4.4 step 2) ====================

    /** The photo of a content upload and its checklist, after the authority checks. */
    public record ContentTarget(SurveyMediaId mediaId, String checklistId) {
    }

    /** The claim of one upload attempt, or the replay answer when these bytes were already received. */
    public record ContentClaim(String token, String receiveKey, Instant claimedAt, MediaContentResult replayOrNull) {

        public boolean isReplay() {
            return replayOrNull != null;
        }

        /** The token and the key are not logged. */
        @Override
        public String toString() {
            return "ContentClaim[" + (isReplay() ? "replay" : claimedAt) + "]";
        }
    }

    /**
     * Content order (2) to (3b) (spec 5.4), answered before the body slots: an unknown or malformed photo 404 without
     * an event; its lead hidden 404 and the caller not the assignee 403, each with a throttled event of resource MEDIA;
     * the uploader at the A-27 cap of expensive outcomes 429. One short transaction.
     */
    public ContentTarget authorizeContent(SurveyViewer viewer, String mediaId) {
        if (!SurveyMediaId.isValid(mediaId)) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(mediaId);
        return inCommand(() -> {
            String checklistId = media.findChecklistIdById(id).orElseThrow(MediaNotFoundException::new);
            try {
                loader.requireMediaAuthority(viewer, checklistId, SecurityResourceType.MEDIA, mediaId,
                        SecurityAction.UPLOAD);
            } catch (ChecklistNotFoundException e) {
                throw new MediaNotFoundException();
            }
            limits.requireExpensiveBelowCap(viewer.userId(), clock.now());
            return new ContentTarget(id, checklistId);
        });
    }

    /**
     * Content order (5) to (12) and the claim (spec 5.4, 4.4 step 2): read-only 409, ineligible 422, takeover required
     * 409, not the creator or removed 409 MEDIA_NOT_AVAILABLE, the upload state (a replay of the same bytes, other
     * bytes received, in progress, expired), uploads off 409, intake paused 503, then the headers against the
     * declaration 400; the claim is a conditional update under the set lock that also enforces the 60-minute window.
     */
    public ContentClaim claimContent(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                     ContentHeaders headers) {
        try {
            return inCommand(() -> claimInTransaction(viewer, viewerCanHold, target, headers));
        } catch (MediaIntakePausedException e) {
            intakeGate.recordRefusal(e);
            throw e;
        }
    }

    private ContentClaim claimInTransaction(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                            ContentHeaders headers) {
        Instant now = clock.now();
        SurveyChecklist checklist = mediaContext(viewer, target).checklist();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        SurveyMedia photo = media.findById(target.mediaId()).orElseThrow(MediaNotFoundException::new);
        requireCreatorAndNotRemoved(viewer, photo);
        switch (photo.getStatus()) {
            case PROCESSING, READY, REJECTED, FAILED, CANCELLED -> {
                if (!photo.getSourceSha256().equals(headers.sha256Hex())) {
                    throw new MediaUploadStateException(MediaUploadStateException.Kind.CONTENT_ALREADY_RECEIVED);
                }
                long setVersion = sets.findForRead(target.checklistId()).map(MediaSet::getSetVersion).orElse(0L);
                return new ContentClaim(null, null, null, new MediaContentResult(items.assemble(photo,
                        viewerCanHold), setVersion, null));
            }
            case RECEIVING -> throw new MediaUploadStateException(MediaUploadStateException.Kind.UPLOAD_IN_PROGRESS);
            case UPLOAD_EXPIRED -> throw new MediaUploadStateException(MediaUploadStateException.Kind.UPLOAD_EXPIRED);
            case AWAITING_CONTENT -> {
                // the upload may start
            }
        }
        if (!switches.uploadsOn()) {
            throw new MediaUploadDisabledException();
        }
        intakeGate.requireOpen();
        if (headers.length() != photo.getDeclaredSizeBytes()) {
            throw new MediaRequestMalformedException("Content-Length", false);
        }
        if (!headers.sha256Hex().equals(photo.getSourceSha256())) {
            throw new MediaRequestMalformedException("Content-Digest", false);
        }
        MediaSet set = sets.lockForUpdate(target.checklistId())
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        String token = ulids.generateId();
        String key = "quarantine/" + target.mediaId().getValue() + "/" + ulids.generateId();
        if (!media.claimIfAwaiting(target.mediaId(), token, key, now, now.minus(SurveyMedia.CLAIM_WINDOW))) {
            boolean twin = media.findById(target.mediaId()).map(SurveyMedia::getStatus)
                    .filter(current -> current == MediaStatus.RECEIVING).isPresent();
            throw new MediaUploadStateException(twin ? MediaUploadStateException.Kind.UPLOAD_IN_PROGRESS
                    : MediaUploadStateException.Kind.UPLOAD_EXPIRED);
        }
        set.bump(now);
        sets.update(set);
        return new ContentClaim(token, key, now, null);
    }

    /**
     * An aborted or refused upload gives its claim back (spec 4.4 step 2): AWAITING_CONTENT again when it still holds
     * this token, the set version bumped. A failure is logged with ids only; the sweeper releases a stale claim later.
     */
    public void releaseContentClaim(ContentTarget target, String token) {
        try {
            commandTransaction.executeWithoutResult(status -> {
                MediaSet set = sets.lockForUpdate(target.checklistId())
                        .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
                if (media.releaseClaimIfToken(target.mediaId(), token)) {
                    set.bump(clock.now());
                    sets.update(set);
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not release the upload claim of survey photo {}", target.mediaId(), e);
        }
    }

    /**
     * The commit, the completion point of BR-17 (spec 4.4 step 2): a fresh transaction re-checks (2) to (11), the
     * creator, the claim token, the placement in the current G1 and the content identity of A-16, in the lock order
     * of 5.4 (lead and checklist shared, G1 shared, set, photo, job, activity); then PROCESSING with the source key,
     * the PROCESS job, the History entry CHECKLIST_MEDIA_ADDED with the category and a set version bump.
     */
    public MediaContentResult commitContent(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                            ContentClaim claim, long sizeBytes) {
        try {
            return inCommand(() -> commitInTransaction(viewer, viewerCanHold, target, claim, sizeBytes));
        } catch (MediaIntakePausedException e) {
            intakeGate.recordRefusal(e);
            throw e;
        }
    }

    private MediaContentResult commitInTransaction(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
                                                   ContentClaim claim, long sizeBytes) {
        Instant now = clock.now();
        CommandContext context = mediaContext(viewer, target);
        // survey-media spec 5.4 (3b), A-27: the cap of expensive outcomes may have been reached while the body streamed
        limits.requireExpensiveBelowCap(viewer.userId(), now);
        SurveyChecklist checklist = context.checklist();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        IdentificationContent g1 = (IdentificationContent) groups.lockForShare(checklist.getId(),
                ChecklistGroup.IDENTIFICATION).document().content();
        MediaSet set = sets.lockForUpdate(target.checklistId())
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        SurveyMedia photo = media.lockForUpdate(List.of(target.mediaId())).stream().findFirst()
                .orElseThrow(MediaNotFoundException::new);
        requireCreatorAndNotRemoved(viewer, photo);
        if (photo.getStatus() != MediaStatus.RECEIVING || !claim.token().equals(photo.getReceiveToken())) {
            throw new MediaUploadStateException(MediaUploadStateException.Kind.UPLOAD_EXPIRED);
        }
        if (!switches.uploadsOn()) {
            throw new MediaUploadDisabledException();
        }
        intakeGate.requireOpen();
        Optional<String> field = PlacementRules.violation(photo.getCategory(), photo.getPlacement().spaceIdOrNull(),
                g1);
        if (field.isPresent()) {
            throw new ChecklistValidationException(List.of(new Violation(field.get(), "spaceId".equals(field.get())
                    ? "spaceId is no longer a space of the checklist." : "category is no longer allowed here.")));
        }
        IdentityVerdict verdict = MediaContentIdentity.check(photo.getSourceSha256(), photo.getId().getValue(),
                revisionPhotos.of(target.checklistId()), media.findCommittedHashOnOtherVisits(
                        context.lead().leadId(), target.checklistId(), photo.getSourceSha256()));
        if (verdict.kind() != IdentityVerdict.Kind.FREE) {
            throw new MediaContentIdentityException(verdict.kind(), verdict.matchingMediaIdOrNull());
        }
        photo.commit(claim.receiveKey(), sizeBytes, now);
        media.update(photo);
        jobs.upsertProcess(photo.getId(), now, now);
        set.bump(now);
        sets.update(set);
        ChecklistActivityResult activity = leads.recordChecklistActivity(context.lead().leadId(),
                new ChecklistActivity(ChecklistActivityType.CHECKLIST_MEDIA_ADDED, viewer.userId(),
                        target.checklistId(), checklist.getChecklistNo(), photo.getCategory().name(), now));
        return new MediaContentResult(items.assemble(photo, viewerCanHold), set.getSetVersion(), activity.entry());
    }

    // ==================== removal (spec 4.7) ====================

    /**
     * Removal order (spec 5.4): (1) the correction's shape, before any lookup → (2) 404 → (3) 403 not the assignee →
     * (4) 409 read-only → (5) 422 ineligible → (6) 409 takeover required; then under the set and photo locks (7) an
     * already removed photo answers as it is → (8) 409 referenced → (9) 400 correction missing where BR-12 needs it,
     * or its date out of range; then the removal: the deletion request and its job (due 10 minutes later, A-17) only
     * for a READY photo that is not held (A-23), the set version bumped, and the History row of a PROCESSING or READY
     * photo (A-10).
     */
    @Override
    public MediaContentResult remove(SurveyViewer viewer, boolean viewerCanHold, RemoveMediaCommand command) {
        List<Violation> violations = new ArrayList<>();
        ReasonBasis correction = command.correctionOrNull() == null ? null : ChecklistInputs.reasonAndBasis(
                "correction.", command.correctionOrNull().reason(), command.correctionOrNull().basis(), violations);
        ChecklistInputs.throwIfAny(violations);
        if (!SurveyMediaId.isValid(command.mediaId())) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(command.mediaId());
        return inCommand(() -> removeInTransaction(viewer, viewerCanHold, id, correction));
    }

    private MediaContentResult removeInTransaction(SurveyViewer viewer, boolean viewerCanHold, SurveyMediaId id,
                                                   ReasonBasis correction) {
        Instant now = clock.now();
        String checklistId = media.findChecklistIdById(id).orElseThrow(MediaNotFoundException::new);
        CommandContext context;
        try {
            context = loader.loadForMediaCommand(viewer, checklistId, SecurityResourceType.MEDIA, id.getValue(),
                    SecurityAction.REMOVE);
        } catch (ChecklistNotFoundException e) {
            throw new MediaNotFoundException();
        }
        SurveyChecklist checklist = context.checklist();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        MediaSet set = sets.lockForUpdate(checklistId)
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        SurveyMedia photo = media.lockForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(MediaNotFoundException::new);
        if (photo.isRemoved()) {
            return new MediaContentResult(items.assemble(photo, viewerCanHold), set.getSetVersion(), null);
        }
        if (references.isReferenced(id)) {
            throw new MediaReferencedException();
        }
        if (correction == null && MediaRemovalPolicy.correctionRequired(photo, viewer.userId())) {
            throw new ChecklistValidationException(List.of(new Violation("correction",
                    "Removing a photo someone else uploaded needs a correction: reason, date and basis.")));
        }
        if (correction != null) {
            List<Violation> dates = new ArrayList<>();
            ChecklistDateRules.requireBasisDate("correction.basis.confirmedOn", correction.basis().confirmedOn(),
                    context.visitFacts().arrivalDate(), SurveyTimeRules.vietnamDate(now)).ifPresent(dates::add);
            ChecklistInputs.throwIfAny(dates);
        }
        boolean history = MediaRemovalPolicy.writesHistory(photo);
        photo.remove(RemovalKind.HOLDER, viewer.userId(), correction == null ? null
                : new MediaRemovalCorrection(correction.reason(), correction.basis()), null, now);
        media.update(photo);
        if (photo.getContentDeleteRequestedAt() != null) {
            jobs.upsertDeleteContent(photo.getId(), now.plus(MediaContentDeletionJob.DELAY), now);
        }
        set.bump(now);
        sets.update(set);
        ChecklistHistoryEntry entry = null;
        if (history) {
            entry = leads.recordChecklistActivity(context.lead().leadId(), new ChecklistActivity(
                    ChecklistActivityType.CHECKLIST_MEDIA_REMOVED, viewer.userId(), checklistId,
                    checklist.getChecklistNo(), photo.getCategory().name(), now)).entry();
        }
        return new MediaContentResult(items.assemble(photo, viewerCanHold), set.getSetVersion(), entry);
    }

    // ==================== deselection (checklist submission spec 4.6, 5.4, A-12) ====================

    /**
     * Deselection order (submission spec 5.5, as a removal, on the revision of the path): (1) the correction's shape,
     * before any lookup → (2) 404 the revision or its lead not visible → (3) 403 not the assignee → 404 a photo not
     * carried into the revision, from a scalar read before any lock, so another lead's rows are never touched and its
     * photos never told apart → (4) 409 read-only → (5) 422 ineligible → (6) 409 takeover required; then under the set
     * and photo locks an already deselected photo changes nothing → (9) 400 correction missing where survey-media BR-12
     * needs it, or its date out of range. The photo row is only read: it is never removed and no deletion is asked for.
     * The History row is a removal's.
     *
     * @return whether the photo left the revision now; false for a repeat
     */
    @Override
    public boolean deselect(SurveyViewer viewer, DeselectMediaCommand command) {
        List<Violation> violations = new ArrayList<>();
        ReasonBasis correction = command.correctionOrNull() == null ? null : ChecklistInputs.reasonAndBasis(
                "correction.", command.correctionOrNull().reason(), command.correctionOrNull().basis(), violations);
        ChecklistInputs.throwIfAny(violations);
        if (!SurveyMediaId.isValid(command.mediaId())) {
            throw new MediaNotFoundException();
        }
        SurveyMediaId id = SurveyMediaId.of(command.mediaId());
        return inCommand(() -> deselectInTransaction(viewer, command.checklistId(), id, correction));
    }

    private boolean deselectInTransaction(SurveyViewer viewer, String checklistId, SurveyMediaId id,
                                          ReasonBasis correction) {
        Instant now = clock.now();
        String origin;
        CommandContext context;
        try {
            // spec A-19: the event names the revision, as a create does: a photo id here is the caller's, unchecked yet
            LeadFacts lead = loader.requireMediaAuthority(viewer, checklistId, SecurityResourceType.MEDIA_SET,
                    checklistId, SecurityAction.DESELECT);
            origin = selections.findCarriedFrom(checklistId, id).orElseThrow(MediaNotFoundException::new);
            context = loader.editableForMedia(viewer, lead, checklistId);
        } catch (ChecklistNotFoundException e) {
            throw new MediaNotFoundException();
        }
        SurveyChecklist checklist = context.checklist();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        LockedSets locked = setLocks.lockFor(origin, id)
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        SurveyMedia photo = media.lockForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(MediaNotFoundException::new);
        MediaSelection selection = selections.findOne(checklistId, id).orElseThrow(MediaNotFoundException::new);
        if (!selection.isSelected()) {
            return false;
        }
        if (correction == null && MediaRemovalPolicy.correctionRequired(photo, viewer.userId())) {
            throw new ChecklistValidationException(List.of(new Violation("correction",
                    "Taking out a photo someone else uploaded needs a correction: reason, date and basis.")));
        }
        if (correction != null) {
            List<Violation> dates = new ArrayList<>();
            ChecklistDateRules.requireBasisDate("correction.basis.confirmedOn", correction.basis().confirmedOn(),
                    context.visitFacts().arrivalDate(), SurveyTimeRules.vietnamDate(now)).ifPresent(dates::add);
            ChecklistInputs.throwIfAny(dates);
        }
        selections.update(selection.deselected(viewer.userId(), RemovalKind.HOLDER, correction == null ? null
                : new MediaRemovalCorrection(correction.reason(), correction.basis()), null, now));
        MediaSet set = locked.all().stream().filter(row -> row.getChecklistId().equals(checklistId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("A revision with carried photos has its media set."));
        set.bump(now);
        sets.update(set);
        if (MediaRemovalPolicy.writesHistory(photo)) {
            leads.recordChecklistActivity(context.lead().leadId(), new ChecklistActivity(
                    ChecklistActivityType.CHECKLIST_MEDIA_REMOVED, viewer.userId(), checklistId,
                    checklist.getChecklistNo(), photo.getCategory().name(), now));
        }
        return true;
    }

    // ==================== security hold (spec A-23) ====================

    /**
     * Hold order (spec 5.4): (1) the reason, before any lookup (the role is checked before this call) → (3) 404 →
     * under the lead and checklist share locks and the set and photo locks: (4) already held answers the photo as it
     * is, nothing bumped and no event → (5) 409 MEDIA_NOT_AVAILABLE field status for a photo that is removed or not
     * PROCESSING or READY; then the hold, the set version bumped and one HOLD/HELD event in the transaction.
     */
    @Override
    public MediaStateResult hold(SurveyViewer viewer, String mediaId, HoldReason reasonOrNull) {
        if (reasonOrNull == null) {
            throw new ChecklistValidationException(List.of(new Violation("reason", "reason is required.")));
        }
        SurveyMediaId id = adminTarget(mediaId);
        return inCommand(() -> {
            Instant now = clock.now();
            AdminLocked locked = lockForAdmin(viewer, id);
            SurveyMedia photo = locked.photo();
            if (photo.isHeld()) {
                return state(photo, locked.sets().origin());
            }
            if (photo.isRemoved() || (photo.getStatus() != MediaStatus.PROCESSING
                    && photo.getStatus() != MediaStatus.READY)) {
                throw new MediaNotAvailableException("status");
            }
            photo.hold(viewer.userId(), reasonOrNull, now);
            media.update(photo);
            setLocks.bumpAll(locked.sets(), now);
            events.recordInTransaction(viewer.userId(), locked.leadId(), SecurityResourceType.MEDIA,
                    id.getValue(), SecurityAction.HOLD, SecurityOutcome.HELD, null);
            return state(photo, locked.sets().origin());
        });
    }

    /**
     * Release order (spec 5.4): (3) 404 → under the locks (4) not held answers the photo as it is → the release
     * ({@code hold_released_at} not before {@code held_at}), for a removed READY photo its deletion request and job in
     * the same transaction, the set version bumped and one RELEASE/RELEASED event.
     */
    @Override
    public MediaStateResult release(SurveyViewer viewer, String mediaId) {
        SurveyMediaId id = adminTarget(mediaId);
        return inCommand(() -> {
            Instant now = clock.now();
            AdminLocked locked = lockForAdmin(viewer, id);
            SurveyMedia photo = locked.photo();
            if (!photo.isHeld()) {
                return state(photo, locked.sets().origin());
            }
            boolean requestedBefore = photo.getContentDeleteRequestedAt() != null;
            photo.release(viewer.userId(), now);
            media.update(photo);
            if (!requestedBefore && photo.getContentDeleteRequestedAt() != null) {
                jobs.upsertDeleteContent(photo.getId(), now.plus(MediaRemovalPolicy.CONTENT_DELETION_DELAY), now);
            }
            setLocks.bumpAll(locked.sets(), now);
            events.recordInTransaction(viewer.userId(), locked.leadId(), SecurityResourceType.MEDIA,
                    id.getValue(), SecurityAction.RELEASE, SecurityOutcome.RELEASED, null);
            return state(photo, locked.sets().origin());
        });
    }

    private static SurveyMediaId adminTarget(String mediaId) {
        if (!SurveyMediaId.isValid(mediaId)) {
            throw new MediaNotFoundException();
        }
        return SurveyMediaId.of(mediaId);
    }

    /**
     * spec 5.4: the lead and checklist FOR SHARE, then the set rows of the photo's origin and of every revision that
     * selects it, in id order (checklist submission spec 5.4), and the photo FOR UPDATE; no group row.
     */
    private AdminLocked lockForAdmin(SurveyViewer viewer, SurveyMediaId id) {
        String checklistId = media.findChecklistIdById(id).orElseThrow(MediaNotFoundException::new);
        String leadId;
        try {
            leadId = loader.lockForAdminMediaCommand(viewer, checklistId).leadId();
        } catch (ChecklistNotFoundException e) {
            throw new MediaNotFoundException();
        }
        LockedSets locked = setLocks.lockFor(checklistId, id)
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        SurveyMedia photo = media.lockForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(MediaNotFoundException::new);
        return new AdminLocked(leadId, locked, photo);
    }

    private MediaStateResult state(SurveyMedia photo, MediaSet set) {
        return new MediaStateResult(items.assemble(photo, true), set.getSetVersion());
    }

    private record AdminLocked(String leadId, LockedSets sets, SurveyMedia photo) {
    }

    /** One command transaction; its deferred refusal events are written once it released its connection. */
    private <T> T inCommand(java.util.function.Supplier<T> work) {
        try {
            return commandTransaction.execute(status -> work.get());
        } finally {
            events.flushDeferred();
        }
    }

    /** Steps (2) to (6) of a photo command; a hidden checklist answers the photo's 404. */
    private CommandContext mediaContext(SurveyViewer viewer, ContentTarget target) {
        try {
            return loader.loadForMediaCommand(viewer, target.checklistId(), SecurityResourceType.MEDIA,
                    target.mediaId().getValue(), SecurityAction.UPLOAD);
        } catch (ChecklistNotFoundException e) {
            throw new MediaNotFoundException();
        }
    }

    /** Content order (8): only the photo's creator sends its bytes, and never for a removed photo. */
    private static void requireCreatorAndNotRemoved(SurveyViewer viewer, SurveyMedia photo) {
        if (!viewer.userId().equals(photo.getUploadedByUserId())) {
            throw new MediaNotAvailableException("uploadedBy");
        }
        if (photo.isRemoved()) {
            throw new MediaNotAvailableException("status");
        }
    }

    /**
     * spec 4.4 step 1: the caller's photo of this upload key on the checklist; the same body answers it again (200),
     * another body is 409 IDEMPOTENCY_KEY_REUSED. Keys are scoped per checklist and uploader.
     *
     * @param lockedSetOrNull the set row when the caller holds its lock, else it is read plainly
     */
    private Optional<MediaCreateResult> replay(SurveyViewer viewer, boolean viewerCanHold, CreateInput input,
                                               MediaSet lockedSetOrNull) {
        Optional<SurveyMedia> stored = media.findByUploadKey(input.checklistId(), viewer.userId(), input.uploadKey());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (!input.sameBodyAs(stored.get())) {
            throw new MediaUploadKeyReusedException();
        }
        long setVersion = lockedSetOrNull != null ? lockedSetOrNull.getSetVersion()
                : sets.findForRead(input.checklistId()).map(MediaSet::getSetVersion).orElse(0L);
        return Optional.of(new MediaCreateResult(items.assemble(stored.get(), viewerCanHold), setVersion, false));
    }

    /** The selected photo of the checklist with the hash, after the backstop refused a twin (spec A-16 (a)). */
    private String selectedWithHash(CreateInput input) {
        return media.findForChecklist(input.checklistId()).stream()
                .filter(photo -> photo.isSelected() && photo.getSourceSha256().equals(input.sha256()))
                .map(photo -> photo.getId().getValue()).findFirst().orElse(null);
    }

    /** spec 5.4 create (10), A-9, A-12: a space of the current G1 at the category's level; SHARED_* only in PARTS. */
    private static MediaPlacement placement(CreateInput input, IdentificationContent g1) {
        Optional<String> field = PlacementRules.violation(input.category(), input.spaceIdOrNull(), g1);
        if (field.isPresent()) {
            String message = "spaceId".equals(field.get()) ? "spaceId is not a space of the checklist."
                    : "category is not allowed for this placement or rental scope.";
            throw new ChecklistValidationException(List.of(new Violation(field.get(), message)));
        }
        if (input.spaceIdOrNull() == null) {
            return MediaPlacement.asset();
        }
        ObservedSpace space = PlacementRules.findSpace(g1, input.spaceIdOrNull());
        return MediaPlacement.space(space.spaceId(), space.code());
    }
}
