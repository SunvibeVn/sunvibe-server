package vn.sunvibe.survey.core.application.service.media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.media.MediaContentIdentityException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaRequestMalformedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadDisabledException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadKeyReusedException;
import vn.sunvibe.survey.core.application.exception.media.MediaUploadStateException;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.query.media.MediaCreateResult;
import vn.sunvibe.survey.core.application.service.media.SurveyMediaCommandService.ContentClaim;
import vn.sunvibe.survey.core.application.service.media.SurveyMediaCommandService.ContentTarget;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.common.RevisionPhotos;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.media.IntakeGate;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaContentHeaders.ContentHeaders;
import vn.sunvibe.survey.core.application.shared.media.MediaInputs.CreateInput;
import vn.sunvibe.survey.core.application.shared.media.MediaItemAssembler;
import vn.sunvibe.survey.core.application.shared.media.MediaLimitCounter;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.MediaPlacement;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.IdentityVerdict;
import vn.sunvibe.survey.core.domain.policy.media.MediaContentIdentity;
import vn.sunvibe.survey.core.domain.policy.media.PlacementRules;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

/**
 * The upload steps that run inside the transactions of {@link SurveyMediaCommandService} (survey-media spec 4.4, 5.4):
 * the create, the content's authority, claim, release and commit, and the replay of an upload key. The service owns
 * every transaction scope and what a refusal does after its rollback.
 */
@Component
public class MediaUploadSteps {

    private final ChecklistContextLoader loader;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaJobDomainRepository jobs;
    private final RevisionPhotos revisionPhotos;
    private final SurveyLeadPort leads;
    private final MediaLimitCounter limits;
    private final IntakeGate intakeGate;
    private final MediaSwitches switches;
    private final MediaItemAssembler items;
    private final MediaClock clock;
    private final IdGenerator<String> ulids;

    public MediaUploadSteps(ChecklistContextLoader loader, SurveyChecklistGroupDomainRepository groups,
                            SurveyMediaDomainRepository media, SurveyMediaSetDomainRepository sets,
                            SurveyMediaJobDomainRepository jobs, RevisionPhotos revisionPhotos, SurveyLeadPort leads,
                            MediaLimitCounter limits, IntakeGate intakeGate, MediaSwitches switches,
                            MediaItemAssembler items, MediaClock clock,
                            @Qualifier("Ulid") IdGenerator<String> ulids) {
        this.loader = loader;
        this.groups = groups;
        this.media = media;
        this.sets = sets;
        this.jobs = jobs;
        this.revisionPhotos = revisionPhotos;
        this.leads = leads;
        this.limits = limits;
        this.intakeGate = intakeGate;
        this.switches = switches;
        this.items = items;
        this.clock = clock;
        this.ulids = ulids;
    }

    MediaCreateResult createInTransaction(SurveyViewer viewer, boolean viewerCanHold, CreateInput input) {
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

    /** Content order (2) to (3b), for {@link SurveyMediaCommandService#authorizeContent}. */
    ContentTarget authorizeInTransaction(SurveyViewer viewer, SurveyMediaId id, String mediaId) {
        String checklistId = media.findChecklistIdById(id).orElseThrow(MediaNotFoundException::new);
        try {
            loader.requireMediaAuthority(viewer, checklistId, SecurityResourceType.MEDIA, mediaId,
                    SecurityAction.UPLOAD);
        } catch (ChecklistNotFoundException e) {
            throw new MediaNotFoundException();
        }
        limits.requireExpensiveBelowCap(viewer.userId(), clock.now());
        return new ContentTarget(id, checklistId);
    }

    ContentClaim claimInTransaction(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
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

    /** The claim's release, for {@link SurveyMediaCommandService#releaseContentClaim}. */
    void releaseInTransaction(ContentTarget target, String token) {
        MediaSet set = sets.lockForUpdate(target.checklistId())
                .orElseThrow(() -> new IllegalStateException("A photo's checklist has its media set."));
        if (media.releaseClaimIfToken(target.mediaId(), token)) {
            set.bump(clock.now());
            sets.update(set);
        }
    }

    MediaContentResult commitInTransaction(SurveyViewer viewer, boolean viewerCanHold, ContentTarget target,
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
    Optional<MediaCreateResult> replay(SurveyViewer viewer, boolean viewerCanHold, CreateInput input,
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
    String selectedWithHash(CreateInput input) {
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
