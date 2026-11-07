package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.checklist.DecideChecklistCommand;
import vn.sunvibe.survey.core.application.command.checklist.StartRevisionCommand;
import vn.sunvibe.survey.core.application.command.checklist.SubmitChecklistCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistDecisionNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotReadyException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReviewNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistSubmissionNotAvailableException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistDecideResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRevisionResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSubmitResult;
import vn.sunvibe.survey.core.application.query.common.LeadState;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistReviewReader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistSwitches;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.checklist.ReviewerExclusionLoader;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.checklist.SubmissionCanonicalForm;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistReviewCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SubmittedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.MediaVariant;
import vn.sunvibe.survey.core.domain.model.media.PlacedMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.SurveyText;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.checklist.DecisionPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.GateFacts;
import vn.sunvibe.survey.core.domain.policy.checklist.GateFailure;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusion;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionContext;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionGate;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionHash;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - submission, review and revisions (survey-checklist submission spec 4.3..4.6, 5.4, 5.5). One
 * transaction per command, every rule read from rows taken under the locks of spec 5.4, in the one lock order: lead,
 * checklist, media sets in id order, photos in id order, activity.
 */
@Service
@Transactional
public class SurveyChecklistReviewCommandService implements SurveyChecklistReviewCommandUseCase {

    /** spec A-10: reason, notes and limitations. */
    private static final int DECISION_TEXT_MAX = 2000;
    /** spec A-10: the reason of a photo mark. */
    private static final int REJECTION_TEXT_MAX = 500;
    private static final String SURVEY_SCHEDULED = "SURVEY_SCHEDULED";

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyChecklistSubmissionDomainRepository submissions;
    private final SurveyChecklistEvidenceDomainRepository evidence;
    private final SurveyChecklistDecisionDomainRepository decisions;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository mediaSets;
    private final SurveyMediaSelectionDomainRepository selections;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final ReviewerExclusionLoader exclusions;
    private final SurveyRecordReader records;
    private final SecurityEventRecorder securityEvents;
    private final ChecklistViewAssembler views;
    private final MediaSwitches mediaSwitches;
    private final ChecklistSwitches checklistSwitches;
    private final SubmissionCanonicalForm canonicalForm;
    private final IdGenerator<String> ulids;
    private final IdGenerator<Long> snowflakes;
    private final TransactionTemplate decisionTransaction;
    private final TransactionTemplate answerTransaction;

    public SurveyChecklistReviewCommandService(SurveyChecklistDomainRepository checklists,
                                               SurveyChecklistGroupDomainRepository groups,
                                               SurveyChecklistTakeoverDomainRepository takeovers,
                                               SurveyChecklistSubmissionDomainRepository submissions,
                                               SurveyChecklistEvidenceDomainRepository evidence,
                                               SurveyChecklistDecisionDomainRepository decisions,
                                               SurveyMediaDomainRepository media,
                                               SurveyMediaSetDomainRepository mediaSets,
                                               SurveyMediaSelectionDomainRepository selections,
                                               SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                               SurveyorDirectoryPort directory, ChecklistContextLoader loader,
                                               ReviewerExclusionLoader exclusions, SurveyRecordReader records,
                                               SecurityEventRecorder securityEvents, ChecklistViewAssembler views,
                                               MediaSwitches mediaSwitches, ChecklistSwitches checklistSwitches,
                                               SubmissionCanonicalForm canonicalForm,
                                               @Qualifier("Ulid") IdGenerator<String> ulids,
                                               @Qualifier("Snowflake") IdGenerator<Long> snowflakes,
                                               PlatformTransactionManager transactionManager) {
        this.checklists = checklists;
        this.groups = groups;
        this.takeovers = takeovers;
        this.submissions = submissions;
        this.evidence = evidence;
        this.decisions = decisions;
        this.media = media;
        this.mediaSets = mediaSets;
        this.selections = selections;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.directory = directory;
        this.loader = loader;
        this.exclusions = exclusions;
        this.records = records;
        this.securityEvents = securityEvents;
        this.views = views;
        this.mediaSwitches = mediaSwitches;
        this.checklistSwitches = checklistSwitches;
        this.canonicalForm = canonicalForm;
        this.ulids = ulids;
        this.snowflakes = snowflakes;
        this.decisionTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction.setReadOnly(true);
    }

    /**
     * Submit check order (spec 5.5): body (400) → revision exists, lead visible (404, event) → assignee (403, event) →
     * read-only (409; revision SUBMITTED by the caller: the replay of A-4) → eligible (422) → holder (409) → version
     * (409) → S2 (409 CHECKLIST_SUBMISSION_NOT_AVAILABLE) → S3..S7 (409 CHECKLIST_NOT_READY). The lead is read under its
     * share lock and never written. A second submit of one revision waits on the checklist lock and replays.
     */
    @Override
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
        List<SurveyMedia> photos = lockSelectedPhotos(checklist.getId().getValue());
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
        List<EvidenceEntry> frozen = photos.stream().map(SurveyChecklistReviewCommandService::entry)
                .sorted(Comparator.comparing(EvidenceEntry::mediaId)).toList();
        submissions.save(submission(checklist, lead, context.visit(), states, frozen, now));
        evidence.saveAll(checklist.getId(), frozen);
        ChecklistActivityResult activity = finish(viewer, lead, checklist, ChecklistActivityType.CHECKLIST_SUBMITTED,
                now);
        return new ChecklistSubmitResult(views.assemble(viewer, lead, locked.ofLead(), checklist), activity.entry());
    }

    /**
     * Decide check order (spec 5.5): role (403, on the endpoint) → body (400) → an account that can sign in (403, plan
     * PD-6) → revision exists, lead visible (404, event) → version (409) → state (409 CHECKLIST_DECISION_NOT_ALLOWED) →
     * excluded (403 CHECKLIST_REVIEW_NOT_ALLOWED, event) → marks among the frozen photos (400) → R2..R6 for PASSED (409).
     * The lead row is locked first, so every rule reads what a hold, a reassignment or a second decision left. No text
     * of the review reaches the lead: its History entry has the outcome and kind, a close reason the fixed one (A-11).
     * The transaction ends before the answer is built, so the lead row is not held for its reads (spec 9.5).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChecklistDecideResult decide(SurveyViewer viewer, DecideChecklistCommand command) {
        DecisionBody body = decisionBody(command);
        if (!directory.canSignIn(viewer.userId())) {
            throw new ChecklistReviewNotAllowedException();
        }
        Decided decided = decisionTransaction.execute(status -> decideInTransaction(viewer, command, body));
        LeadFacts after = decided.lead();
        return answerTransaction.execute(status -> new ChecklistDecideResult(views.assemble(viewer, after,
                decided.ofLead(), decided.checklist()), new LeadState(after.leadId(), after.status(), after.version(),
                after.lastActivityAt()), decided.entry()));
    }

    /** What a committed decision leaves for its answer. */
    private record Decided(SurveyChecklist checklist, LeadFacts lead, List<SurveyAppointment> ofLead,
                           LeadHistoryEntry entry) {
    }

    private Decided decideInTransaction(SurveyViewer viewer, DecideChecklistCommand command, DecisionBody body) {
        ReadContext locked = loader.lockForDecision(viewer, command.checklistId());
        SurveyChecklist checklist = locked.checklist();
        LeadFacts lead = locked.lead();
        if (!command.expectedVersion().equals(checklist.getVersion())) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        boolean onCurrentVisit = CurrentVisitPolicy.currentVisit(locked.ofLead(), lead.intentCycle())
                .filter(visit -> visit.getId().equals(checklist.getVisitAppointmentId())).isPresent();
        List<String> state = DecisionPolicy.stateBlockers(checklist.getStatus(), lead.status(),
                checklist.getIntentCycle(), lead.intentCycle(), onCurrentVisit, mediaSwitches.servingOn());
        if (!state.isEmpty()) {
            throw new ChecklistDecisionNotAllowedException(state);
        }
        SurveyAppointment visit = ChecklistContextLoader.visitOf(checklist, locked.ofLead());
        if (ReviewerExclusion.excluded(exclusions.load(checklist, visit), lead.assignedUserId())
                .contains(viewer.userId())) {
            securityEvents.refused(viewer.userId(), lead.leadId(), SecurityResourceType.CHECKLIST,
                    command.checklistId(), SecurityAction.REVIEW, SecurityOutcome.NOT_INDEPENDENT);
            throw new ChecklistReviewNotAllowedException();
        }
        List<EvidenceEntry> frozen = evidence.findByChecklistId(checklist.getId());
        DecisionPolicy.checkRejections(body.rejections(), frozen.stream().map(EvidenceEntry::mediaId)
                .collect(Collectors.toSet()));
        if (command.outcome() == DecisionOutcome.PASSED) {
            requirePassable(checklist, visit, locked.ofLead(), frozen, body.rejections());
        }

        Instant now = SurveyChecklistCommandService.now();
        LeadMoveTarget target = target(command);
        checklist.decide(command.outcome(), now);
        try {
            checklists.update(checklist);
        } catch (OptimisticLockingFailureException e) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        decisions.save(new ChecklistDecision(checklist.getId(), command.outcome(), command.needsChangesKindOrNull(),
                command.failedKindOrNull(), body.reason(), body.notes(), body.limitations(), viewer.userId(), now,
                lead.status(), target == LeadMoveTarget.KEEP ? lead.status() : target.name(), body.rejections()));
        LeadMoveResult moved = leads.apply(lead.leadId(), lead.version(), new LeadMove(LeadMoveEvent.CHECKLIST_REVIEWED,
                target, viewer.userId(), checklist.getId().getValue(), checklist.getChecklistNo(), now,
                detail(command), closeReason(command, checklist)));
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now,
                AccessResourceType.CHECKLIST, checklist.getId().getValue()));
        return new Decided(checklist, lead.moved(moved.status(), moved.version(), moved.lastActivityAt()),
                locked.ofLead(), moved.entry());
    }

    /**
     * Start revision check order (spec 5.5): body (400) → visible (404, event) → assignee (403, event) → state (409
     * CHECKLIST_NOT_ALLOWED on status) → a later revision (409 CHECKLIST_ALREADY_STARTED) → eligible (422) → intake
     * needed or absent (400). Locks (spec 5.4): lead and previous revision FOR SHARE, the new rows, then the origin sets
     * and photos. Groups keep content and per-unit metadata, so a copied value keeps who entered it (S-12).
     */
    @Override
    public ChecklistRevisionResult startRevision(SurveyViewer viewer, StartRevisionCommand command) {
        List<Violation> violations = new ArrayList<>();
        ReasonBasis intake = command.intakeOrNull() == null ? null : ChecklistInputs.reasonAndBasis("intake.",
                command.intakeOrNull().reason(), command.intakeOrNull().basis(), violations);
        ChecklistInputs.throwIfAny(violations);

        ReadContext locked = loader.lockPrevious(viewer, command.checklistId());
        SurveyChecklist previous = locked.checklist();
        LeadFacts lead = locked.lead();
        SurveyRecordReader.CycleRecord record = records.read(lead.leadId(), lead.intentCycle(), locked.ofLead());
        boolean onCurrentVisit = record.currentVisit().filter(visit -> visit.getId()
                .equals(previous.getVisitAppointmentId())).isPresent();
        if (!SURVEY_SCHEDULED.equals(lead.status()) || previous.getIntentCycle() != lead.intentCycle()
                || !onCurrentVisit || !returnedForDocuments(record, previous)) {
            throw new ChecklistNotAllowedException("status", List.of());
        }
        if (!record.latestOfCurrentVisit().map(SurveyChecklist::getId).filter(previous.getId()::equals).isPresent()) {
            throw new ChecklistAlreadyStartedException(null);
        }
        if (!directory.isEligible(lead.assignedUserId())) {
            throw new SurveyorIneligibleException(null);
        }
        Instant now = SurveyChecklistCommandService.now();
        SurveyAppointment visit = ChecklistContextLoader.visitOf(previous, locked.ofLead());
        boolean holder = viewer.userId().equals(previous.getHolderUserId());
        if (holder && intake != null) {
            violations.add(new Violation("intake", "intake must be empty: you held the previous revision."));
        } else if (!holder && command.intakeOrNull() == null) {
            violations.add(new Violation("intake",
                    "intake is required: say where your information about this visit comes from."));
        } else if (intake != null) {
            ChecklistDateRules.requireBasisDate("intake.basis.confirmedOn", intake.basis().confirmedOn(),
                    ChecklistContextLoader.visitFacts(visit).arrivalDate(), SurveyTimeRules.vietnamDate(now))
                    .ifPresent(violations::add);
        }
        ChecklistInputs.throwIfAny(violations);

        SurveyChecklist next = SurveyChecklist.startAfter(SurveyChecklistId.of(ulids.generateId()), lead.leadId(),
                checklists.nextChecklistNo(lead.leadId()), previous.getVisitAppointmentId(), previous.getIntentCycle(),
                viewer.userId(), now, previous.getId());
        checklists.insert(next);
        Map<ChecklistGroup, GroupState> copied = new EnumMap<>(ChecklistGroup.class);
        groups.findAll(previous.getId()).forEach((group, state) -> copied.put(group, new GroupState(state.document(),
                0L, null, null)));
        groups.insertAll(next.getId(), copied);
        if (intake != null) {
            takeovers.save(ChecklistTakeover.revisionByOther(snowflakes.generateId(), next.getId(),
                    previous.getHolderUserId(), viewer.userId(), intake.reason(), intake.basis(), now));
        }
        String nextId = next.getId().getValue();
        mediaSets.insertIgnoringExisting(nextId, now);
        List<MediaSelection> carried = lockSelectedPhotos(previous.getId().getValue()).stream()
                .filter(SurveyMedia::isServable)
                .map(photo -> MediaSelection.carried(nextId, photo.getId(), photo.getChecklistId(), now)).toList();
        if (!carried.isEmpty()) {
            selections.insertAll(carried);
        }
        ChecklistActivityResult activity = finish(viewer, lead, next, ChecklistActivityType.CHECKLIST_REVISION_STARTED,
                now);
        return new ChecklistRevisionResult(views.assemble(viewer, lead, locked.ofLead(), next), activity.entry());
    }

    private static boolean returnedForDocuments(SurveyRecordReader.CycleRecord record, SurveyChecklist revision) {
        return record.mark(revision).needsChangesKindOrNull() == NeedsChangesKind.DOCUMENTS_ONLY;
    }

    /** The texts of a decision, cleaned (spec A-10), with its photo marks. */
    private record DecisionBody(String reason, String notes, String limitations, List<EvidenceRejection> rejections) {

        @Override
        public String toString() {
            return "DecisionBody[hidden]";
        }
    }

    /** spec 5.5 body: A-10 on the texts and R1 on the shape, every violation at once. */
    private static DecisionBody decisionBody(DecideChecklistCommand command) {
        List<Violation> violations = new ArrayList<>();
        if (command.expectedVersion() == null) {
            violations.add(new Violation("expectedVersion", "expectedVersion is required."));
        }
        String reason = text("reason", command.reasonOrNull(), DECISION_TEXT_MAX, violations);
        String notes = text("notes", command.notesOrNull(), DECISION_TEXT_MAX, violations);
        String limitations = text("limitations", command.limitationsOrNull(), DECISION_TEXT_MAX, violations);
        List<EvidenceRejection> rejections = new ArrayList<>();
        if (command.rejectedEvidence() == null) {
            violations.add(new Violation("rejectedEvidence", "rejectedEvidence is required."));
        } else {
            for (int i = 0; i < command.rejectedEvidence().size(); i++) {
                DecideChecklistCommand.RejectedEvidence mark = command.rejectedEvidence().get(i);
                String path = "rejectedEvidence." + i;
                String markReason = mark == null ? null : text(path + ".reason", mark.reason(), REJECTION_TEXT_MAX,
                        violations);
                if (mark == null || mark.mediaId() == null) {
                    violations.add(new Violation(path + ".mediaId", path + ".mediaId is required."));
                } else if (markReason == null) {
                    violations.add(new Violation(path + ".reason", path + ".reason is required."));
                } else {
                    rejections.add(new EvidenceRejection(mark.mediaId(), markReason));
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new ChecklistValidationException(violations);
        }
        DecisionPolicy.checkBody(command.outcome(), command.needsChangesKindOrNull(), command.failedKindOrNull(),
                reason, limitations, rejections);
        return new DecisionBody(reason, notes, limitations, rejections);
    }

    private static String text(String path, String value, int max, List<Violation> violations) {
        try {
            return SurveyText.optional(path, value, max);
        } catch (InvalidSurveyValueException e) {
            violations.add(new Violation(path, e.getMessage()));
            return null;
        }
    }

    /** R2..R6 (spec 4.5) from the frozen G1 and G4 and the frozen photos as they stand now. */
    private void requirePassable(SurveyChecklist checklist, SurveyAppointment visit, List<SurveyAppointment> ofLead,
                                 List<EvidenceEntry> frozen, List<EvidenceRejection> rejections) {
        Map<ChecklistGroup, GroupState> states = groups.findAll(checklist.getId());
        List<SurveyMedia> photos = media.findByIds(frozen.stream().map(EvidenceEntry::mediaId).map(SurveyMediaId::of)
                .toList());
        Readiness readiness = views.readiness(checklist, states, visit, photos, SubmissionContext.CLOSED);
        List<String> blockers = ChecklistReviewReader.passedBlockers(readiness, states, photos, rejections.stream()
                .map(EvidenceRejection::mediaId).collect(Collectors.toSet()), ofLead);
        if (!blockers.isEmpty()) {
            throw new ChecklistDecisionNotAllowedException(blockers);
        }
    }

    /** The outcome table of spec 4.5. */
    private static LeadMoveTarget target(DecideChecklistCommand command) {
        return switch (command.outcome()) {
            case PASSED -> LeadMoveTarget.QUALIFIED;
            case NEEDS_CHANGES -> command.needsChangesKindOrNull() == NeedsChangesKind.DOCUMENTS_ONLY
                    ? LeadMoveTarget.KEEP : LeadMoveTarget.CONTACTED;
            case FAILED -> command.failedKindOrNull() == FailedKind.TEMPORARY ? LeadMoveTarget.CONTACTED
                    : LeadMoveTarget.REJECTED;
        };
    }

    /** spec 4.9: the History detail, {@code <OUTCOME>[:<KIND>]}; never a text. */
    private static String detail(DecideChecklistCommand command) {
        return switch (command.outcome()) {
            case PASSED -> command.outcome().name();
            case NEEDS_CHANGES -> command.outcome().name() + ":" + command.needsChangesKindOrNull().name();
            case FAILED -> command.outcome().name() + ":" + command.failedKindOrNull().name();
        };
    }

    /** spec A-11: the fixed close reason of a rejection; the reviewer's own reason stays in the decision. */
    private static String closeReason(DecideChecklistCommand command, SurveyChecklist checklist) {
        if (command.failedKindOrNull() == FailedKind.OUT_OF_SCOPE) {
            return "Survey failed: out of scope (checklist #" + checklist.getChecklistNo() + ")";
        }
        if (command.failedKindOrNull() == FailedKind.NOT_ACCEPTED) {
            return "Survey failed: not accepted (checklist #" + checklist.getChecklistNo() + ")";
        }
        return null;
    }

    /**
     * spec 5.4: the set rows of the revision and of its carried photos' origins in id order, then the selected photos
     * in id order. Ids come from scalar reads, so no photo is loaded before its lock.
     */
    private List<SurveyMedia> lockSelectedPhotos(String checklistId) {
        List<MediaSelection> carried = selections.findByChecklistId(checklistId).stream()
                .filter(MediaSelection::isSelected).toList();
        Set<String> setIds = new TreeSet<>();
        setIds.add(checklistId);
        carried.forEach(selection -> setIds.add(selection.carriedFromChecklistId()));
        setIds.forEach(mediaSets::lockForUpdate);
        List<SurveyMediaId> ids = new ArrayList<>(media.findSelectedPlacements(checklistId).stream()
                .map(PlacedMedia::id).toList());
        carried.forEach(selection -> ids.add(selection.mediaId()));
        return media.lockForUpdate(ids);
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

    /** The access-log row of the answer, then the History entry, the last write (spec 5.4). */
    private ChecklistActivityResult finish(SurveyViewer viewer, LeadFacts lead, SurveyChecklist checklist,
                                           ChecklistActivityType type, Instant now) {
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now,
                AccessResourceType.CHECKLIST, checklist.getId().getValue()));
        return leads.recordChecklistActivity(lead.leadId(), new ChecklistActivity(type, viewer.userId(),
                checklist.getId().getValue(), checklist.getChecklistNo(), null, now));
    }
}
