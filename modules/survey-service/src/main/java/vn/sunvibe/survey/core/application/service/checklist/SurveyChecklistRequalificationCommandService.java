package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.checklist.RequalifyCommand;
import vn.sunvibe.survey.core.application.exception.common.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRequalifyResult;
import vn.sunvibe.survey.core.application.query.common.LeadState;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadHistoryEntry;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistRequalificationCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.common.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.policy.common.SurveyText;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.checklist.RequalificationPolicy;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistRequalificationDomainRepository;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Use Case Implementation - the requalification of a PASSED revision (property-create requalification spec 4, 5.3). One
 * transaction, in the lock order of the decision: lead, checklist, activity. Check order: role (403, on the endpoint) →
 * body (400) → revision exists, lead visible (404) → version (409) → the current unmarked PASSED of the current cycle
 * (409) → lead status (409) → the basis date against the decision day (400). Only roles that see every lead pass the role
 * gate, so a refusal has no security event to record, and the chain refuses a locked account, so the actor's sign-in is
 * not checked. No text of the mark reaches the lead (survey BR-4). The transaction ends before the answer is built, so
 * the lead row is not held for its reads (submission spec 9.5).
 */
@Service
@Transactional
public class SurveyChecklistRequalificationCommandService implements SurveyChecklistRequalificationCommandUseCase {

    /** spec A-6. */
    private static final int REASON_MAX = 2000;
    /** spec A-6: the source and the scope. */
    private static final int DETAIL_MAX = 500;
    private static final String CHECKLIST = "checklist";
    private static final String NOT_CURRENT_MESSAGE = "This revision is not the lead's current PASSED revision.";

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistRequalificationDomainRepository requalifications;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final ChecklistContextLoader loader;
    private final SurveyRecordReader records;
    private final ChecklistViewAssembler views;
    private final IdGenerator<Long> snowflakes;
    private final TransactionTemplate markTransaction;
    private final TransactionTemplate answerTransaction;

    public SurveyChecklistRequalificationCommandService(SurveyChecklistDomainRepository checklists,
                                                        SurveyChecklistRequalificationDomainRepository requalifications,
                                                        SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                                        ChecklistContextLoader loader, SurveyRecordReader records,
                                                        ChecklistViewAssembler views,
                                                        @Qualifier("Snowflake") IdGenerator<Long> snowflakes,
                                                        PlatformTransactionManager transactionManager) {
        this.checklists = checklists;
        this.requalifications = requalifications;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.loader = loader;
        this.records = records;
        this.views = views;
        this.snowflakes = snowflakes;
        this.markTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction = new TransactionTemplate(transactionManager);
        this.answerTransaction.setReadOnly(true);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChecklistRequalifyResult mark(SurveyViewer viewer, RequalifyCommand command) {
        MarkBody body = markBody(command);
        Marked marked = markTransaction.execute(status -> markInTransaction(viewer, command, body));
        LeadFacts after = marked.lead();
        return answerTransaction.execute(status -> new ChecklistRequalifyResult(views.assemble(viewer, after,
                marked.ofLead(), marked.checklist()), new LeadState(after.leadId(), after.statusName(), after.version(),
                after.lastActivityAt()), marked.entry()));
    }

    /** What a committed mark leaves for its answer. */
    private record Marked(SurveyChecklist checklist, LeadFacts lead, List<SurveyAppointment> ofLead,
                          LeadHistoryEntry entry) {
    }

    private Marked markInTransaction(SurveyViewer viewer, RequalifyCommand command, MarkBody body) {
        // submission plan PD-8: the lead's lock, then the checklist's first load under its own, so the row read here is
        // the one a concurrent mark, decision or hold left
        ReadContext locked = loader.lockForDecision(viewer, command.checklistId());
        SurveyChecklist checklist = locked.checklist();
        LeadFacts lead = locked.lead();
        if (!command.expectedVersion().equals(checklist.getVersion())) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        boolean current = records.read(lead.leadId(), lead.intentCycle(), locked.ofLead()).passed()
                .filter(passed -> passed.getId().equals(checklist.getId())).isPresent();
        if (!current) {
            throw new SurveyNotAllowedException(CHECKLIST, List.of(), NOT_CURRENT_MESSAGE);
        }
        if (!RequalificationPolicy.leadAllows(lead.status())) {
            throw new InvalidSurveyStateException(List.of());
        }
        Instant now = SurveyChecklistCommandService.now();
        List<Violation> violations = new ArrayList<>();
        ChecklistDateRules.requireRequalificationDate("basisDate", command.basisDate(),
                SurveyTimeRules.vietnamDate(checklist.getDecidedAt()), SurveyTimeRules.vietnamDate(now))
                .ifPresent(violations::add);
        ChecklistInputs.throwIfAny(violations);

        LeadMoveTarget target = lead.status() == LeadStatus.QUALIFIED ? LeadMoveTarget.CONTACTED : LeadMoveTarget.KEEP;
        checklist.markRequalified(now);
        try {
            checklists.update(checklist);
        } catch (OptimisticLockingFailureException e) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        requalifications.save(new ChecklistRequalification(checklist.getId(), command.reasonKind(), body.reason(),
                body.eventSource(), body.scope(), command.basisDate(), viewer.userId(), now, lead.statusName(),
                target == LeadMoveTarget.KEEP ? lead.statusName() : target.name()));
        LeadMoveResult moved = move(lead, target, viewer, checklist, now);
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now,
                AccessResourceType.CHECKLIST, checklist.getId().getValue()));
        return new Marked(checklist, lead.moved(moved.statusName(), moved.version(), moved.lastActivityAt()),
                locked.ofLead(), moved.entry());
    }

    private LeadMoveResult move(LeadFacts lead, LeadMoveTarget target, SurveyViewer viewer, SurveyChecklist checklist,
                                Instant now) {
        try {
            return leads.apply(lead.leadId(), lead.version(), new LeadMove(LeadMoveEvent.REQUALIFICATION_MARKED, target,
                    viewer.userId(), checklist.getId().getValue(), checklist.getChecklistNo(), now));
        } catch (LeadVersionConflictException e) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        } catch (LeadMoveRefusedException e) {
            throw new InvalidSurveyStateException(List.of());
        }
    }

    /** The texts of a mark, cleaned (spec A-6). */
    private record MarkBody(String reason, String eventSource, String scope) {

        @Override
        public String toString() {
            return "MarkBody[hidden]";
        }
    }

    /** spec 5.3 body: every violation at once. */
    private static MarkBody markBody(RequalifyCommand command) {
        List<Violation> violations = new ArrayList<>();
        if (command.expectedVersion() == null) {
            violations.add(new Violation("expectedVersion", "expectedVersion is required."));
        }
        if (command.reasonKind() == null) {
            violations.add(new Violation("reasonKind", "reasonKind is required."));
        }
        if (command.basisDate() == null) {
            violations.add(new Violation("basisDate", "basisDate is required."));
        }
        String reason = text("reason", command.reason(), REASON_MAX, violations);
        String eventSource = text("eventSource", command.eventSource(), DETAIL_MAX, violations);
        String scope = text("scope", command.scope(), DETAIL_MAX, violations);
        ChecklistInputs.throwIfAny(violations);
        return new MarkBody(reason, eventSource, scope);
    }

    private static String text(String field, String value, int max, List<Violation> violations) {
        try {
            return SurveyText.requireStorable(field, value, max);
        } catch (InvalidSurveyValueException e) {
            violations.add(new Violation(field, e.getMessage()));
            return null;
        }
    }
}
