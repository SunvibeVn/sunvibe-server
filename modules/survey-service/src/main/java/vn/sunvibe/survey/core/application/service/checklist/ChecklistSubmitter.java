package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.command.checklist.SubmitChecklistCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotReadyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistSubmissionNotAvailableException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSubmitResult;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistSwitches;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.SubmissionCanonicalForm;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.SubmittedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.policy.checklist.GateFacts;
import vn.sunvibe.survey.core.domain.policy.checklist.GateFailure;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionContext;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionGate;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionHash;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Submits a checklist revision for {@link SurveyChecklistReviewCommandService} (survey-checklist submission spec 4.3,
 * 5.4, 5.5), in the service's transaction: the revision and its selected photos are frozen with their hash.
 */
@Component
public class ChecklistSubmitter {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistSubmissionDomainRepository submissions;
    private final SurveyChecklistEvidenceDomainRepository evidence;
    private final ChecklistContextLoader loader;
    private final ChecklistViewAssembler views;
    private final MediaSwitches mediaSwitches;
    private final ChecklistSwitches checklistSwitches;
    private final SubmissionCanonicalForm canonicalForm;
    private final ChecklistReviewSteps steps;

    public ChecklistSubmitter(SurveyChecklistDomainRepository checklists, SurveyChecklistGroupDomainRepository groups,
                              SurveyChecklistSubmissionDomainRepository submissions,
                              SurveyChecklistEvidenceDomainRepository evidence, ChecklistContextLoader loader,
                              ChecklistViewAssembler views, MediaSwitches mediaSwitches,
                              ChecklistSwitches checklistSwitches, SubmissionCanonicalForm canonicalForm,
                              ChecklistReviewSteps steps) {
        this.checklists = checklists;
        this.groups = groups;
        this.submissions = submissions;
        this.evidence = evidence;
        this.loader = loader;
        this.views = views;
        this.mediaSwitches = mediaSwitches;
        this.checklistSwitches = checklistSwitches;
        this.canonicalForm = canonicalForm;
        this.steps = steps;
    }

    /**
     * Submit check order (spec 5.5): body (400) → revision exists, lead visible (404, event) → assignee (403, event) →
     * read-only (409; revision SUBMITTED by the caller: the replay of A-4) → eligible (422) → holder (409) → version
     * (409) → S2 (409 CHECKLIST_SUBMISSION_NOT_AVAILABLE) → S3..S7 (409 CHECKLIST_NOT_READY). The lead is read under its
     * share lock and never written. A second submit of one revision waits on the checklist lock and replays.
     */
    public ChecklistSubmitResult submit(SurveyViewer viewer, SubmitChecklistCommand command) {
        if (command.expectedVersion() == null) {
            throw new ChecklistValidationException(List.of(new Violation("expectedVersion",
                    "expectedVersion is required.")));
        }
        ReadContext locked = loader.lockForWrite(viewer, command.checklistId(), SecurityAction.SUBMIT);
        SurveyChecklist checklist = locked.checklist();
        LeadFacts lead = locked.lead();
        if (checklist.getStatus() == ChecklistStatus.SUBMITTED && checklist.getIntentCycle() == lead.intentCycle()
                && viewer.userId().equals(checklist.getSubmittedByUserId())) {
            return new ChecklistSubmitResult(views.assemble(viewer, lead, locked.ofLead(), checklist), null);
        }
        CommandContext context = loader.requireEditable(viewer, locked);
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        if (!command.expectedVersion().equals(checklist.getVersion())) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        if (!mediaSwitches.uploadsOn() || !checklistSwitches.submissionEnabled()) {
            throw new ChecklistSubmissionNotAvailableException();
        }
        List<SurveyMedia> photos = steps.lockSelectedPhotos(checklist.getId().getValue());
        Map<ChecklistGroup, GroupState> states = groups.findAll(checklist.getId());
        boolean scheduled = ChecklistViewAssembler.hasScheduled(locked.ofLead());
        Readiness readiness = views.readiness(checklist, states, context.visit(), photos,
                new SubmissionContext(true, true, scheduled));
        List<GateFailure> failures = SubmissionGate.check(new GateFacts(true, true, scheduled, readiness.missing(),
                photos));
        if (!failures.isEmpty()) {
            throw new ChecklistNotReadyException(failures.stream().map(GateFailure::field).toList());
        }

        Instant now = SurveyChecklistCommandService.now();
        checklist.submit(viewer.userId(), now);
        try {
            checklists.update(checklist);
        } catch (OptimisticLockingFailureException e) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        List<EvidenceEntry> frozen = photos.stream().map(ChecklistSubmitter::entry)
                .sorted(Comparator.comparing(EvidenceEntry::mediaId)).toList();
        submissions.save(submission(checklist, lead, context.visit(), states, frozen, now));
        evidence.saveAll(checklist.getId(), frozen);
        ChecklistActivityResult activity = steps.finish(viewer, lead, checklist,
                ChecklistActivityType.CHECKLIST_SUBMITTED, now);
        return new ChecklistSubmitResult(views.assemble(viewer, lead, locked.ofLead(), checklist), activity.entry());
    }

    /** spec 4.3: the photo with the hash of its FULL variant, of its latest attempt. */
    private static EvidenceEntry entry(SurveyMedia photo) {
        MediaVariant full = photo.getVariants().stream().filter(variant -> variant.kind() == VariantKind.FULL)
                .max(Comparator.comparingInt(MediaVariant::attempt))
                .orElseThrow(() -> new IllegalStateException("A READY photo has its FULL variant."));
        return new EvidenceEntry(photo.getId().getValue(), photo.getCategory(), photo.getPlacement().spaceIdOrNull(),
                photo.getPlacement().spaceCodeAtUploadOrNull(), full.sha256());
    }

    private ChecklistSubmission submission(SurveyChecklist checklist, LeadFacts lead, SurveyAppointment visit,
                                           Map<ChecklistGroup, GroupState> states, List<EvidenceEntry> frozen,
                                           Instant now) {
        IdentificationContent g1 = ChecklistViewAssembler.identification(states);
        Map<ChecklistGroup, GroupContent> contents = new EnumMap<>(ChecklistGroup.class);
        Map<ChecklistGroup, Long> versions = new EnumMap<>(ChecklistGroup.class);
        states.forEach((group, state) -> {
            contents.put(group, state.document().content());
            versions.put(group, state.version());
        });
        VisitRecord record = visit.getVisit();
        return new ChecklistSubmission(checklist.getId(), checklist.getSubmittedByUserId(), now,
                checklist.getTemplateCode(), checklist.getTemplateVersion(), checklist.getKind(), lead.intent(),
                checklist.getIntentCycle(), checklist.getVisitAppointmentId(), record.performedBy(),
                record.arrivedAt(), record.recordedBy(), record.recordedAt(), g1.rentalScope(),
                g1.propertyType().isValue() ? g1.propertyType().value() : null,
                g1.spaces().stream().map(space -> new SubmittedSpace(space.spaceId(), space.code())).toList(),
                versions, ChecklistSubmission.MEDIA_V1_NO_PERMISSION,
                SubmissionHash.of(canonicalForm.of(contents, frozen)));
    }
}
