package vn.sunvibe.survey.core.application.service.media;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.media.MediaNotFoundException;
import vn.sunvibe.survey.core.application.exception.media.MediaReferencedException;
import vn.sunvibe.survey.core.application.query.media.MediaContentResult;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.media.EvidenceReferencePort;
import vn.sunvibe.survey.core.application.shared.media.MediaClock;
import vn.sunvibe.survey.core.application.shared.media.MediaItemAssembler;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks;
import vn.sunvibe.survey.core.application.shared.media.MediaSetLocks.LockedSets;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.MediaRemovalCorrection;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.media.MediaRemovalPolicy;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaJobDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

/**
 * The removal and deselection steps that run inside the transactions of {@link SurveyMediaCommandService}
 * (survey-media spec 4.7, 5.4; survey-checklist submission spec 4.6, A-12).
 */
@Component
public class MediaRemovalSteps {

    private final ChecklistContextLoader loader;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository sets;
    private final SurveyMediaJobDomainRepository jobs;
    private final SurveyMediaSelectionDomainRepository selections;
    private final MediaSetLocks setLocks;
    private final SurveyLeadPort leads;
    private final EvidenceReferencePort references;
    private final MediaItemAssembler items;
    private final MediaClock clock;

    public MediaRemovalSteps(ChecklistContextLoader loader, SurveyMediaDomainRepository media,
                             SurveyMediaSetDomainRepository sets, SurveyMediaJobDomainRepository jobs,
                             SurveyMediaSelectionDomainRepository selections, MediaSetLocks setLocks,
                             SurveyLeadPort leads, EvidenceReferencePort references, MediaItemAssembler items,
                             MediaClock clock) {
        this.loader = loader;
        this.media = media;
        this.sets = sets;
        this.jobs = jobs;
        this.selections = selections;
        this.setLocks = setLocks;
        this.leads = leads;
        this.references = references;
        this.items = items;
        this.clock = clock;
    }

    MediaContentResult removeInTransaction(SurveyViewer viewer, boolean viewerCanHold, SurveyMediaId id,
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

    boolean deselectInTransaction(SurveyViewer viewer, String checklistId, SurveyMediaId id,
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
}
