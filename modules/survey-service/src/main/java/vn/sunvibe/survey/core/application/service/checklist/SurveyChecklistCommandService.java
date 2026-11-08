package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.command.checklist.SaveChecklistGroupCommand;
import vn.sunvibe.survey.core.application.command.checklist.StartChecklistCommand;
import vn.sunvibe.survey.core.application.command.checklist.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistStartResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistTakeoverResult;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistView;
import vn.sunvibe.survey.core.application.query.common.LeadState;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistMediaCascade;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.StartContext;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeoverRow;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverCheckpoint;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverKind;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.common.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.repository.appointment.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Use Case Implementation - checklist commands (survey-checklist spec 4.5, 4.8, 5.5). Each command is one transaction:
 * the body is checked first, then the lead id by a scalar query and the lead row under a share lock, the caller and the
 * states in the order of survey-checklist spec 5.5, then the survey rows, the access log and, last, the lead History entry, which is an
 * activity row only: the lead's version and status never change. The answer is assembled after the writes. A save
 * runs through {@link ChecklistGroupWriter}, and its checks through {@link ChecklistGroupChecks}.
 */
@Service
@Transactional
public class SurveyChecklistCommandService implements SurveyChecklistCommandUseCase {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final ChecklistViewAssembler views;
    private final ChecklistMediaCascade mediaCascade;
    private final ChecklistGroupWriter writer;
    private final IdGenerator<String> ulids;
    private final IdGenerator<Long> snowflakes;

    public SurveyChecklistCommandService(SurveyChecklistDomainRepository checklists,
                                         SurveyChecklistGroupDomainRepository groups,
                                         SurveyChecklistTakeoverDomainRepository takeovers,
                                         SurveyAppointmentDomainRepository appointments,
                                         SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                         SurveyorDirectoryPort directory, ChecklistContextLoader loader,
                                         ChecklistViewAssembler views, ChecklistMediaCascade mediaCascade,
                                         ChecklistGroupWriter writer, @Qualifier("Ulid") IdGenerator<String> ulids,
                                         @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.checklists = checklists;
        this.groups = groups;
        this.takeovers = takeovers;
        this.appointments = appointments;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.directory = directory;
        this.loader = loader;
        this.views = views;
        this.mediaCascade = mediaCascade;
        this.writer = writer;
        this.ulids = ulids;
        this.snowflakes = snowflakes;
    }

    /**
     * Start check order (spec 5.5): body → appointment and visible lead (404) → assignee (403) → the visit and the lead
     * (409 CHECKLIST_NOT_ALLOWED on appointmentId, intent, status) → a draft already (409) → eligible (422) → the
     * intake declaration: required exactly when the caller did not perform the visit, its date from the arrival date
     * to today (400).
     */
    @Override
    public ChecklistStartResult start(SurveyViewer viewer, StartChecklistCommand command) {
        List<Violation> violations = new ArrayList<>();
        ReasonBasis intake = command.intakeOrNull() == null ? null : ChecklistInputs.reasonAndBasis("intake.",
                command.intakeOrNull().reason(), command.intakeOrNull().basis(), violations);
        ChecklistInputs.throwIfAny(violations);

        StartContext context = loader.loadForStart(viewer, command.appointmentId());
        LeadFacts lead = context.lead();
        SurveyAppointment appointment = context.appointment();
        Optional<SurveyAppointment> current = CurrentVisitPolicy.currentVisit(context.ofLead(), lead.intentCycle());
        if (appointment.getStatus() != AppointmentStatus.VISITED || current.isEmpty()
                || !current.get().getId().equals(appointment.getId())) {
            throw new ChecklistNotAllowedException("appointmentId", List.of());
        }
        if (appointment.getIntent() != SurveyIntent.LONG_TERM_RENT) {
            throw new ChecklistNotAllowedException("intent", List.of(SurveyIntent.LONG_TERM_RENT.name()));
        }
        if (lead.status() != LeadStatus.SURVEY_SCHEDULED) {
            throw new ChecklistNotAllowedException("status", List.of(LeadStatus.SURVEY_SCHEDULED.name()));
        }
        // survey-checklist submission spec 4.6: a visit gets its first revision here; every later one comes from "start revision"
        List<SurveyChecklist> ofLead = checklists.findByLeadId(lead.leadId());
        if (ofLead.stream().anyMatch(checklist -> checklist.getVisitAppointmentId().equals(appointment.getId()))) {
            throw new ChecklistAlreadyStartedException(null);
        }
        if (!directory.isEligible(lead.assignedUserId())) {
            throw new SurveyorIneligibleException(null);
        }
        Instant now = now();
        boolean performer = viewer.userId().equals(appointment.getVisit().performedBy());
        if (performer && command.intakeOrNull() != null) {
            violations.add(new Violation("intake", "intake must be empty: you performed this visit."));
        } else if (!performer && command.intakeOrNull() == null) {
            violations.add(new Violation("intake",
                    "intake is required: say where your information about this visit comes from."));
        } else if (intake != null) {
            ChecklistDateRules.requireBasisDate("intake.basis.confirmedOn", intake.basis().confirmedOn(),
                    ChecklistContextLoader.visitFacts(appointment).arrivalDate(), SurveyTimeRules.vietnamDate(now))
                    .ifPresent(violations::add);
        }
        ChecklistInputs.throwIfAny(violations);

        // survey-checklist submission spec A-2: the revision links the last submitted one of the cycle, of an earlier visit
        SurveyChecklistId previous = ofLead.stream().filter(earlier -> earlier.getSubmittedAt() != null
                        && earlier.getIntentCycle() == lead.intentCycle())
                .max(Comparator.comparingInt(SurveyChecklist::getChecklistNo)).map(SurveyChecklist::getId)
                .orElse(null);
        SurveyChecklist checklist = SurveyChecklist.startAfter(SurveyChecklistId.of(ulids.generateId()), lead.leadId(),
                checklists.nextChecklistNo(lead.leadId()), appointment.getId(), appointment.getIntentCycle(),
                viewer.userId(), now, previous);
        checklists.insert(checklist);
        Map<ChecklistGroup, GroupState> initial = new EnumMap<>(ChecklistGroup.class);
        for (ChecklistGroup group : ChecklistGroup.stored()) {
            initial.put(group, new GroupState(GroupDocument.initial(group), 0L, null, null));
        }
        groups.insertAll(checklist.getId(), initial);
        if (intake != null) {
            takeovers.save(ChecklistTakeoverRow.startByOther(snowflakes.generateId(), checklist.getId(),
                    viewer.userId(), intake.reason(), intake.basis(), now));
        }
        ChecklistActivityResult activity = finish(viewer, lead, checklist, ChecklistActivityType.CHECKLIST_STARTED,
                null, now);
        ChecklistView view = views.assemble(viewer, lead, appointments.findByLeadId(lead.leadId()), checklist);
        return new ChecklistStartResult(view, new LeadState(lead.leadId(), lead.statusName(), lead.version(),
                activity.lastActivityAt()), activity.entry());
    }

    /**
     * Takeover check order (spec 5.5): body → checklist and visible lead (404) → assignee (403) → read-only (409) →
     * eligible (422) → the caller already holds it (409 holder) → checklist version (409 expectedVersion) → basis date
     * from the arrival date to today (400). The checklist row is updated before the takeover row with its checkpoint
     * of every group as read (spec 4.5).
     */
    @Override
    public ChecklistTakeoverResult takeOver(SurveyViewer viewer, TakeOverChecklistCommand command) {
        List<Violation> violations = new ArrayList<>();
        if (command.expectedVersion() == null) {
            violations.add(new Violation("expectedVersion", "expectedVersion is required."));
        }
        ReasonBasis input = ChecklistInputs.reasonAndBasis("", command.reason(), command.basis(), violations);
        ChecklistInputs.throwIfAny(violations);

        CommandContext context = loader.loadForCommand(viewer, command.checklistId(), SecurityAction.TAKEOVER);
        SurveyChecklist checklist = context.checklist();
        if (viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistNotAllowedException("holder", List.of());
        }
        if (!command.expectedVersion().equals(checklist.getVersion())) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        Instant now = now();
        ChecklistDateRules.requireBasisDate("basis.confirmedOn", input.basis().confirmedOn(),
                context.visitFacts().arrivalDate(), SurveyTimeRules.vietnamDate(now)).ifPresent(violations::add);
        ChecklistInputs.throwIfAny(violations);

        String previousHolder = checklist.takeOver(viewer.userId());
        try {
            checklists.update(checklist);
        } catch (OptimisticLockingFailureException e) {
            // spec 5.5: a concurrent takeover changed the row first; answered on the version the caller sent
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        // survey-media spec 4.8, A-22: in this transaction, after the checklist update (survey-media spec 5.4)
        mediaCascade.endOpenUploads(checklist.getId().getValue(), previousHolder, now);
        Map<ChecklistGroup, GroupState> before = groups.findAll(checklist.getId());
        takeovers.save(new ChecklistTakeoverRow(snowflakes.generateId(), checklist.getId(),
                takeovers.findNextTakeoverNo(checklist.getId()), TakeoverKind.TAKEOVER, previousHolder,
                viewer.userId(), input.reason(), input.basis(), new TakeoverCheckpoint(before), now));
        ChecklistActivityResult activity = finish(viewer, context.lead(), checklist,
                ChecklistActivityType.CHECKLIST_TAKEN_OVER, null, now);
        return new ChecklistTakeoverResult(views.assemble(viewer, context.lead(), context.ofLead(), checklist),
                activity.entry());
    }

    /**
     * Saves one group through {@link ChecklistGroupWriter#save}, which holds the check order of spec 5.5; then the
     * access-log row of the answer and, when a group was written, the History entry, the last write (spec A-10).
     */
    @Override
    public ChecklistView save(SurveyViewer viewer, SaveChecklistGroupCommand command) {
        Instant now = now();
        ChecklistGroupWriter.Saved saved = writer.save(viewer, command, now);
        CommandContext context = saved.context();
        SurveyChecklist checklist = context.checklist();
        if (saved.written()) {
            finish(viewer, context.lead(), checklist, ChecklistActivityType.CHECKLIST_SAVED, command.group().name(),
                    now);
        } else {
            log(viewer, context.lead(), checklist, now);
        }
        return views.assemble(viewer, context.lead(), context.ofLead(), checklist);
    }

    private void log(SurveyViewer viewer, LeadFacts lead, SurveyChecklist checklist, Instant now) {
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now,
                AccessResourceType.CHECKLIST, checklist.getId().getValue()));
    }

    /** The access-log row of the answer, then the History entry, the last write (spec 5.5, A-10). */
    ChecklistActivityResult finish(SurveyViewer viewer, LeadFacts lead, SurveyChecklist checklist,
                                   ChecklistActivityType type, String detailOrNull, Instant now) {
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now,
                AccessResourceType.CHECKLIST, checklist.getId().getValue()));
        return leads.recordChecklistActivity(lead.leadId(), new ChecklistActivity(type, viewer.userId(),
                checklist.getId().getValue(), checklist.getChecklistNo(), detailOrNull, now));
    }

    /** Microseconds, as PostgreSQL stores them, so an answer shows the time a later read returns. */
    static Instant now() {
        return AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS);
    }
}
