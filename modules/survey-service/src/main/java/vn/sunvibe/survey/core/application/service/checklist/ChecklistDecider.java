package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.survey.core.application.command.checklist.DecideChecklistCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistDecisionNotAllowedException;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistReviewNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistDecideResult;
import vn.sunvibe.survey.core.application.query.common.LeadState;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.ReviewerExclusionLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.common.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.checklist.DecisionPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusion;
import vn.sunvibe.survey.core.domain.policy.common.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.common.SurveyText;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Decides a submitted checklist revision for {@link SurveyChecklistReviewCommandService} (survey-checklist submission
 * spec 4.2, 4.5, 5.4, 5.5). The service calls it outside any transaction: the decision, its rows and the lead move run
 * in one transaction of their own, and the answer is read in a read-only one after it ends (spec 9.5).
 */
@Component
public class ChecklistDecider {

    /** spec A-10: reason, notes and limitations. */
    private static final int DECISION_TEXT_MAX = 2000;
    /** spec A-10: the reason of a photo mark. */
    private static final int REJECTION_TEXT_MAX = 500;

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistEvidenceDomainRepository evidence;
    private final SurveyChecklistDecisionDomainRepository decisions;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final ReviewerExclusionLoader exclusions;
    private final SecurityEventRecorder securityEvents;
    private final ChecklistViewAssembler views;
    private final MediaSwitches mediaSwitches;
    private final ChecklistReviewSteps steps;
    private final TransactionTemplate decisionTransaction;
    private final TransactionTemplate answerTransaction;

    public ChecklistDecider(SurveyChecklistDomainRepository checklists,
                            SurveyChecklistEvidenceDomainRepository evidence,
                            SurveyChecklistDecisionDomainRepository decisions, SurveyLeadPort leads,
                            SurveyorDirectoryPort directory, ChecklistContextLoader loader,
                            ReviewerExclusionLoader exclusions, SecurityEventRecorder securityEvents,
                            ChecklistViewAssembler views, MediaSwitches mediaSwitches, ChecklistReviewSteps steps,
                            PlatformTransactionManager transactionManager) {
        this.checklists = checklists;
        this.evidence = evidence;
        this.decisions = decisions;
        this.leads = leads;
        this.directory = directory;
        this.loader = loader;
        this.exclusions = exclusions;
        this.securityEvents = securityEvents;
        this.views = views;
        this.mediaSwitches = mediaSwitches;
        this.steps = steps;
        this.decisionTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction.setReadOnly(true);
    }

    /**
     * Decide check order (spec 5.5): role (403, on the endpoint) → body (400) → an account that can sign in (403, plan
     * PD-6) → revision exists, lead visible (404, event) → version (409) → state (409 CHECKLIST_DECISION_NOT_ALLOWED) →
     * excluded (403 CHECKLIST_REVIEW_NOT_ALLOWED, event) → marks among the frozen photos (400) → R2..R6 for PASSED (409).
     * The lead row is locked first, so every rule reads what a hold, a reassignment or a second decision left. No text
     * of the review reaches the lead: its History entry has the outcome and kind, a close reason the fixed one (A-11).
     * The transaction ends before the answer is built, so the lead row is not held for its reads (spec 9.5).
     */
    ChecklistDecideResult decide(SurveyViewer viewer, DecideChecklistCommand command) {
        DecisionBody body = decisionBody(command);
        if (!directory.canSignIn(viewer.userId())) {
            throw new ChecklistReviewNotAllowedException();
        }
        Decided decided = decisionTransaction.execute(status -> decideInTransaction(viewer, command, body));
        LeadFacts after = decided.lead();
        return answerTransaction.execute(status -> new ChecklistDecideResult(views.assemble(viewer, after,
                decided.ofLead(), decided.checklist()),
                new LeadState(after.leadId(), after.statusName(), after.version(), after.lastActivityAt()),
                decided.entry()));
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
            steps.requirePassable(checklist, visit, locked.ofLead(), frozen, body.rejections());
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
                lead.statusName(), target == LeadMoveTarget.KEEP ? lead.statusName() : target.name(),
                body.rejections()));
        LeadMoveResult moved = leads.apply(lead.leadId(), lead.version(), new LeadMove(LeadMoveEvent.CHECKLIST_REVIEWED,
                target, viewer.userId(), checklist.getId().getValue(), checklist.getChecklistNo(), now,
                detail(command), closeReason(command, checklist)));
        steps.logAccess(viewer, lead, checklist, now);
        return new Decided(checklist, lead.moved(moved.statusName(), moved.version(), moved.lastActivityAt()),
                locked.ofLead(), moved.entry());
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
}
