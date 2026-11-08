package vn.sunvibe.survey.core.application.service.media;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotAvailableException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.query.media.MediaStateResult;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaItemAssembler;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks.LockedSets;
import vn.sunvibe.survey.core.domain.model.media.HoldReason;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.media.MediaRemovalPolicy;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;

/**
 * The security hold steps that run inside the transactions of {@link SurveyMediaCommandService} (survey-media spec
 * A-23, 5.4): the hold and the release under the lead, checklist, set and photo locks, with their event.
 */
@Component
public class MediaHoldSteps {

    private final ChecklistContextLoader loader;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaJobDomainRepository jobs;
    private final MediaSetLocks setLocks;
    private final SecurityEventRecorder events;
    private final MediaItemAssembler items;
    private final MediaClock clock;

    public MediaHoldSteps(ChecklistContextLoader loader, SurveyMediaDomainRepository media,
                          SurveyMediaJobDomainRepository jobs, MediaSetLocks setLocks, SecurityEventRecorder events,
                          MediaItemAssembler items, MediaClock clock) {
        this.loader = loader;
        this.media = media;
        this.jobs = jobs;
        this.setLocks = setLocks;
        this.events = events;
        this.items = items;
        this.clock = clock;
    }

    /** The hold under its locks, for {@link SurveyMediaCommandService#hold}. */
    MediaStateResult holdInTransaction(SurveyViewer viewer, SurveyMediaId id, HoldReason reasonOrNull) {
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
    }

    /** The release under its locks, for {@link SurveyMediaCommandService#release}. */
    MediaStateResult releaseInTransaction(SurveyViewer viewer, SurveyMediaId id) {
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
}
