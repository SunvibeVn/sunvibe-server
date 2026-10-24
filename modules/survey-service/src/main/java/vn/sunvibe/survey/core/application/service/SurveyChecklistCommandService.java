package vn.sunvibe.survey.core.application.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.command.SaveChecklistGroupCommand;
import vn.sunvibe.survey.core.application.command.StartChecklistCommand;
import vn.sunvibe.survey.core.application.command.TakeOverChecklistCommand;
import vn.sunvibe.survey.core.application.exception.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.ChecklistTakeoverRequiredException;
import vn.sunvibe.survey.core.application.exception.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.ChecklistStartResult;
import vn.sunvibe.survey.core.application.query.ChecklistTakeoverResult;
import vn.sunvibe.survey.core.application.query.ChecklistView;
import vn.sunvibe.survey.core.application.query.LeadState;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader.CommandContext;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader.StartContext;
import vn.sunvibe.survey.core.application.shared.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.StoredDataRules;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceCheck;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceViolation;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.SurveyChecklistCommandUseCase;
import vn.sunvibe.survey.core.domain.exception.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSnapshot;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.Correction;
import vn.sunvibe.survey.core.domain.model.checklist.GroupContent;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedLocation;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedPlaceNames;
import vn.sunvibe.survey.core.domain.model.checklist.ObservedSpace;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverKind;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChangeKind;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.Tombstone;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitChange;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitDiff;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitKey;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.checklist.CascadeResult;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectedUnit;
import vn.sunvibe.survey.core.domain.policy.checklist.CorrectionPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.GroupCascade;
import vn.sunvibe.survey.core.domain.policy.checklist.GroupDocuments;
import vn.sunvibe.survey.core.domain.policy.checklist.InputForm;
import vn.sunvibe.survey.core.domain.policy.checklist.MetaRules;
import vn.sunvibe.survey.core.domain.policy.checklist.SourceRules;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmittedForm;
import vn.sunvibe.survey.core.domain.policy.checklist.TemplateV1Rules;
import vn.sunvibe.survey.core.domain.policy.checklist.VisitFacts;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case Implementation - checklist commands (survey-checklist spec 4.5, 4.8, 5.5). Each command is one transaction:
 * the body is checked first, then the lead id by a scalar query and the lead row under a share lock, the caller and the
 * states in the order of spec 5.5, then the survey rows, the access log and, last, the lead History entry, which is an
 * activity row only: the lead's version and status never change. The answer is assembled after the writes.
 */
@Service
@Transactional
public class SurveyChecklistCommandService implements SurveyChecklistCommandUseCase {

    private static final String SURVEY_SCHEDULED = "SURVEY_SCHEDULED";

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyChecklistSaveDomainRepository saves;
    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final ChecklistViewAssembler views;
    private final IdGenerator<String> ulids;
    private final IdGenerator<Long> snowflakes;

    public SurveyChecklistCommandService(SurveyChecklistDomainRepository checklists,
                                         SurveyChecklistGroupDomainRepository groups,
                                         SurveyChecklistTakeoverDomainRepository takeovers,
                                         SurveyChecklistSaveDomainRepository saves,
                                         SurveyAppointmentDomainRepository appointments,
                                         SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                         SurveyorDirectoryPort directory, ChecklistContextLoader loader,
                                         ChecklistViewAssembler views, @Qualifier("Ulid") IdGenerator<String> ulids,
                                         @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.checklists = checklists;
        this.groups = groups;
        this.takeovers = takeovers;
        this.saves = saves;
        this.appointments = appointments;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.directory = directory;
        this.loader = loader;
        this.views = views;
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
        if (!SURVEY_SCHEDULED.equals(lead.status())) {
            throw new ChecklistNotAllowedException("status", List.of(SURVEY_SCHEDULED));
        }
        if (checklists.findByLeadId(lead.leadId()).stream().anyMatch(checklist ->
                checklist.getStatus() == ChecklistStatus.DRAFT
                        && checklist.getVisitAppointmentId().equals(appointment.getId()))) {
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

        SurveyChecklist checklist = SurveyChecklist.start(SurveyChecklistId.of(ulids.generateId()), lead.leadId(),
                checklists.nextChecklistNo(lead.leadId()), appointment.getId(), appointment.getIntentCycle(),
                viewer.userId(), now);
        checklists.insert(checklist);
        Map<ChecklistGroup, GroupState> initial = new EnumMap<>(ChecklistGroup.class);
        for (ChecklistGroup group : ChecklistGroup.stored()) {
            initial.put(group, new GroupState(GroupDocument.initial(group), 0L, null, null));
        }
        groups.insertAll(checklist.getId(), initial);
        if (intake != null) {
            takeovers.save(ChecklistTakeover.startByOther(snowflakes.generateId(), checklist.getId(),
                    viewer.userId(), intake.reason(), intake.basis(), now));
        }
        ChecklistActivityResult activity = finish(viewer, lead, checklist, ChecklistActivityType.CHECKLIST_STARTED,
                null, now);
        ChecklistView view = views.assemble(viewer, lead, appointments.findByLeadId(lead.leadId()), checklist);
        return new ChecklistStartResult(view, new LeadState(lead.leadId(), lead.status(), lead.version(),
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
        Map<ChecklistGroup, GroupState> before = groups.findAll(checklist.getId());
        takeovers.save(new ChecklistTakeover(snowflakes.generateId(), checklist.getId(),
                takeovers.findNextTakeoverNo(checklist.getId()), TakeoverKind.TAKEOVER, previousHolder,
                viewer.userId(), input.reason(), input.basis(), new ChecklistSnapshot(before), now));
        ChecklistActivityResult activity = finish(viewer, context.lead(), checklist,
                ChecklistActivityType.CHECKLIST_TAKEN_OVER, null, now);
        return new ChecklistTakeoverResult(views.assemble(viewer, context.lead(), context.ofLead(), checklist),
                activity.entry());
    }

    /**
     * Save check order (spec 5.5): (1) body, correction shape and the G1 catalog and pin, before any lookup → (2)..(5)
     * checklist, visible lead, assignee, read-only, eligible → (6) holder → locks, no group row read before its lock:
     * a G2..G8 save shares the G1 row then locks its own; a G1 save locks G1, reads the others, cascades and locks each
     * group it changes in group order → (7) the canonical content equal to the stored one and no cascade: a no-op →
     * (8) versions → (9) rules that need stored data, on changed units → (10) a correction where the rule asks for one.
     * Then one save-log row per written group, the correction on the named group's row, the access log and the
     * History entry last.
     */
    @Override
    public ChecklistView save(SurveyViewer viewer, SaveChecklistGroupCommand command) {
        Instant now = now();
        LocalDate today = SurveyTimeRules.vietnamDate(now);
        List<Violation> violations = new ArrayList<>();
        if (command.expectedVersion() == null) {
            violations.add(new Violation("expectedVersion", "expectedVersion is required."));
        }
        if (command.content() == null || command.content().group() != command.group()) {
            violations.add(new Violation("content", "content is required."));
            ChecklistInputs.throwIfAny(violations);
        }
        GroupContent body = normalised(command.content(), today, violations);
        ReasonBasis correction = command.correctionOrNull() == null ? null : ChecklistInputs.reasonAndBasis(
                "correction.", command.correctionOrNull().reason(), command.correctionOrNull().basis(), violations);
        ObservedPlaceNames placeNames = null;
        if (body instanceof IdentificationContent g1) {
            PlacedAddress placed = placeAddress(g1, violations);
            body = placed.content();
            placeNames = placed.names();
        }
        ChecklistInputs.throwIfAny(violations);

        CommandContext context = loader.loadForCommand(viewer, command.checklistId(), SecurityAction.SAVE);
        SurveyChecklist checklist = context.checklist();
        if (!viewer.userId().equals(checklist.getHolderUserId())) {
            throw new ChecklistTakeoverRequiredException();
        }
        SurveyChecklistId id = checklist.getId();
        ChecklistGroup group = command.group();
        GroupState stored;
        IdentificationContent storedG1;
        IdentificationContent nextG1;
        Map<ChecklistGroup, GroupState> cascaded = new LinkedHashMap<>();
        Map<ChecklistGroup, GroupContent> cascadedContent = new LinkedHashMap<>();
        if (group == ChecklistGroup.IDENTIFICATION) {
            stored = groups.lockForUpdate(id, ChecklistGroup.IDENTIFICATION);
            storedG1 = (IdentificationContent) stored.document().content();
            nextG1 = (IdentificationContent) body;
            Map<ChecklistGroup, GroupState> others = groups.findOthers(id, ChecklistGroup.IDENTIFICATION);
            Map<ChecklistGroup, GroupDocument> documents =
                    new EnumMap<>(ChecklistGroup.class);
            others.forEach((other, state) -> documents.put(other, state.document()));
            CascadeResult cascade = GroupCascade.of(storedG1, nextG1, documents);
            for (Map.Entry<ChecklistGroup, GroupContent> entry : cascade.changed().entrySet()) {
                cascaded.put(entry.getKey(), groups.lockForUpdate(id, entry.getKey()));
                cascadedContent.put(entry.getKey(), entry.getValue());
            }
        } else {
            GroupState g1State = groups.lockForShare(id, ChecklistGroup.IDENTIFICATION);
            storedG1 = (IdentificationContent) g1State.document().content();
            nextG1 = storedG1;
            stored = groups.lockForUpdate(id, group);
        }
        GroupContent next = TemplateV1Rules.canonical(body, nextG1);

        // (7) spec A-17: nothing to write, also with a stale version; only the access-log row of the answer
        if (cascaded.isEmpty() && next.equals(InputForm.ofStored(stored.document(), storedG1))) {
            log(viewer, context.lead(), checklist, now);
            return views.assemble(viewer, context.lead(), context.ofLead(), checklist);
        }
        // (8)
        if (command.expectedVersion() != stored.version()) {
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        for (Map.Entry<ChecklistGroup, GroupState> entry : cascaded.entrySet()) {
            Long sent = command.otherGroupVersions().get(entry.getKey());
            if (sent == null || sent != entry.getValue().version()) {
                throw new SurveyConcurrentModificationException("otherGroupVersions." + entry.getKey().pathName());
            }
        }
        // (9)
        List<UnitChange> changes = UnitDiff.between(stored.document(), storedG1, body, nextG1);
        Map<ChecklistGroup, List<UnitChange>> cascadeChanges = new LinkedHashMap<>();
        cascadedContent.forEach((other, content) -> cascadeChanges.put(other, UnitDiff.between(
                cascaded.get(other).document(), storedG1, content, nextG1)));
        Optional<SubmittedForm> form = leads.ownerForm(context.lead().leadId()).map(SurveyChecklistCommandService::form);
        StoredDataRules.unknownSpaces(body, storedG1, violations);
        StoredDataRules.scopeNulls(body, nextG1.rentalScope(), violations);
        if (group == ChecklistGroup.SAFETY) {
            EarlierChain chain = earlierChain(checklist, context);
            StoredDataRules.previousLinks(changes, chain.hazardIds(), violations);
            StoredDataRules.reportDates(changes, hazard -> chain.firstSightingArrival(hazard,
                    context.visitFacts().arrivalDate()), today, violations);
        }
        Set<String> leadStaff = views.leadStaff(context.lead(), context.ofLead());
        violations.addAll(SourceRules.check(changes, viewer.userId(), leadStaff, form, nextG1.rentalScope()));
        if (correction != null) {
            ChecklistDateRules.requireBasisDate("correction.basis.confirmedOn", correction.basis().confirmedOn(),
                    context.visitFacts().arrivalDate(), today).ifPresent(violations::add);
        }
        ChecklistInputs.throwIfAny(violations);
        // (10)
        Set<UnitKey> required = new LinkedHashSet<>(CorrectionPolicy.requiring(stored.document(), changes,
                viewer.userId(), context.visitFacts()));
        cascadeChanges.forEach((other, otherChanges) -> required.addAll(CorrectionPolicy.requiring(
                cascaded.get(other).document(), otherChanges, viewer.userId(), context.visitFacts())));
        if (!required.isEmpty() && correction == null) {
            throw new ChecklistValidationException(List.of(new Violation("correction",
                    "Changing values someone else recorded needs a correction: reason, date and basis.")));
        }

        long savedId = snowflakes.generateId();
        Set<UnitKey> covered = new LinkedHashSet<>();
        if (correction != null) {
            if (required.isEmpty()) {
                changes.forEach(change -> covered.add(change.key()));
                cascadeChanges.values().forEach(list -> list.forEach(change -> covered.add(change.key())));
            } else {
                covered.addAll(required);
            }
        }
        Correction recorded = correction == null ? null : new Correction(correction.reason(), correction.basis(),
                covered.stream().map(key -> new CorrectedUnit(key.value(), spaceCode(key, storedG1))).toList());
        write(id, group, stored, next, changes, covered, savedId, recorded, viewer, now, context, form,
                group == ChecklistGroup.IDENTIFICATION ? placeNames : null);
        for (Map.Entry<ChecklistGroup, GroupContent> entry : cascadedContent.entrySet()) {
            ChecklistGroup other = entry.getKey();
            write(id, other, cascaded.get(other), TemplateV1Rules.canonical(entry.getValue(), nextG1),
                    cascadeChanges.get(other), covered, savedId, null, viewer, now, context, form, null);
        }
        finish(viewer, context.lead(), checklist, ChecklistActivityType.CHECKLIST_SAVED, group.name(), now);
        return views.assemble(viewer, context.lead(), context.ofLead(), checklist);
    }

    /**
     * One group's next document, row and save-log row (spec 5.2): meta for every changed unit that stays and the
     * removal stamp of every removed one (survey-checklist BR-29, spec A-29: the caller as last editor), pointing to
     * the named row when a correction covered it; the correction itself only on the named group's row.
     */
    private void write(SurveyChecklistId id, ChecklistGroup group, GroupState stored, GroupContent next,
                       List<UnitChange> changes, Set<UnitKey> covered, long namedSaveId, Correction correction,
                       SurveyViewer viewer, Instant now, CommandContext context, Optional<SubmittedForm> form,
                       ObservedPlaceNames placeNames) {
        VisitFacts visit = context.visitFacts();
        Map<UnitKey, UnitMeta> changedMeta = new HashMap<>();
        Map<UnitKey, Tombstone> removalStamps = new HashMap<>();
        for (UnitChange change : changes) {
            if (change.kind() == ChangeKind.REMOVED) {
                Tombstone stamp = MetaRules.removed(change, viewer.userId(), now);
                removalStamps.put(change.key(), covered.contains(change.key()) ? stamp.withCorrection(namedSaveId)
                        : stamp);
            } else {
                UnitMeta meta = MetaRules.next(change, viewer.userId(), now, visit, form);
                changedMeta.put(change.key(), covered.contains(change.key()) ? meta.withCorrection(namedSaveId)
                        : meta);
            }
        }
        GroupDocument document = GroupDocuments.apply(stored.document(), next, changedMeta, removalStamps);
        if (group == ChecklistGroup.IDENTIFICATION) {
            document = document.withPlaceNames(placeNames);
        }
        GroupState written;
        try {
            written = groups.update(id, group, stored.version(), new GroupState(document, stored.version(), now,
                    viewer.userId()));
        } catch (OptimisticLockingFailureException e) {
            // spec 5.5: the row changed after its lock was read; answered on the version the caller sent
            throw new SurveyConcurrentModificationException("expectedVersion");
        }
        long saveId = correction != null ? namedSaveId : snowflakes.generateId();
        saves.save(new ChecklistSave(saveId, id, group, written.version(), viewer.userId(), now, correction));
    }

    /**
     * Step 1: the template rules; their violations join the list and the body as sent is kept so the other checks
     * can still run.
     */
    private static GroupContent normalised(GroupContent content, LocalDate today, List<Violation> violations) {
        try {
            return TemplateV1Rules.checkAndNormalise(content, today);
        } catch (ChecklistValidationException e) {
            violations.addAll(e.violations());
            return content;
        }
    }

    /** The G1 content with the catalog's pin, and the names to keep (spec PD-1), when the address is a value. */
    private record PlacedAddress(IdentificationContent content, ObservedPlaceNames names) {
    }

    /** Step 1: province, ward and pin by the intake location rules through the lead port (spec 4.6). */
    private PlacedAddress placeAddress(IdentificationContent g1, List<Violation> violations) {
        SourcedField<ObservedLocation> address = g1.observedAddress();
        ObservedLocation value = address == null || !address.isValue() ? null : address.value();
        // The shape rules already refused missing codes or half a pin; the catalog checks the rest
        if (value == null || isBlank(value.provinceCode()) || isBlank(value.wardCode())
                || (value.latitude() == null) != (value.longitude() == null)) {
            return new PlacedAddress(g1, null);
        }
        PlaceCheck check = leads.checkPlace(value.provinceCode(), value.wardCode(), value.latitude(),
                value.longitude());
        for (PlaceViolation violation : check.violations()) {
            String path = "content.observedAddress.value." + violation.field();
            violations.add(new Violation(path, violation.message()));
        }
        if (!check.violations().isEmpty()) {
            return new PlacedAddress(g1, null);
        }
        ObservedLocation placed = new ObservedLocation(value.addressText(), value.provinceCode(), value.wardCode(),
                check.latitude(), check.longitude());
        return new PlacedAddress(new IdentificationContent(new SourcedField<>(address.state(), placed,
                address.naReason(), address.source(), address.confirmedOn(), address.receivedByUserId(),
                address.relayBasis()), g1.differenceNote(), g1.propertyType(), g1.fit(), g1.rentalScope(),
                g1.spaces(), g1.otherPartsNote(), g1.generalNote()),
                new ObservedPlaceNames(check.provinceName(), check.wardName()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** The earlier checklists of the lead's cycle: their hazard ids, and the first sighting of a linked hazard. */
    private record EarlierChain(Map<String, Set<String>> hazardIds, Map<String, Map<String, Hazard>> hazards,
                                Map<String, LocalDate> arrivals) {

        /** spec A-7: the arrival date of the visit of the first hazard of the chain; this visit's when unlinked. */
        LocalDate firstSightingArrival(Hazard hazard, LocalDate ownArrival) {
            LocalDate arrival = ownArrival;
            HazardLink link = hazard.previous();
            Set<HazardLink> seen = new LinkedHashSet<>();
            while (link != null && seen.add(link) && hazards.containsKey(link.checklistId())) {
                arrival = arrivals.get(link.checklistId());
                Hazard earlier = hazards.get(link.checklistId()).get(link.hazardId());
                link = earlier == null ? null : earlier.previous();
            }
            return arrival;
        }
    }

    private EarlierChain earlierChain(SurveyChecklist checklist, CommandContext context) {
        List<SurveyChecklist> earlier = checklists.findByLeadId(checklist.getLeadId()).stream()
                .filter(other -> other.getIntentCycle() == checklist.getIntentCycle()
                        && other.getChecklistNo() < checklist.getChecklistNo())
                .toList();
        Map<String, Set<String>> ids = new HashMap<>();
        Map<String, Map<String, Hazard>> hazards = new HashMap<>();
        Map<String, LocalDate> arrivals = new HashMap<>();
        if (earlier.isEmpty()) {
            return new EarlierChain(ids, hazards, arrivals);
        }
        var earlierGroups = groups.findAllOf(earlier.stream().map(SurveyChecklist::getId).toList());
        for (SurveyChecklist other : earlier) {
            SafetyContent safety = (SafetyContent) earlierGroups.get(other.getId()).get(ChecklistGroup.SAFETY)
                    .document().content();
            Map<String, Hazard> byId = new LinkedHashMap<>();
            if (safety.hazards().isValue()) {
                safety.hazards().value().forEach(hazard -> byId.put(hazard.hazardId(), hazard));
            }
            hazards.put(other.getId().getValue(), byId);
            ids.put(other.getId().getValue(), byId.keySet());
            arrivals.put(other.getId().getValue(), ChecklistContextLoader.visitFacts(ChecklistContextLoader.visitOf(
                    other, context.ofLead())).arrivalDate());
        }
        return new EarlierChain(ids, hazards, arrivals);
    }

    /** The code of the unit's space in the stored G1, so a removed space keeps its code (spec 5.2). */
    private static String spaceCode(UnitKey key, IdentificationContent storedG1) {
        String spaceId = key.spaceId();
        if (spaceId == null) {
            return null;
        }
        return storedG1.spaces().stream().filter(space -> space.spaceId().equals(spaceId))
                .map(ObservedSpace::code).findFirst().orElse(null);
    }

    /** The owner's form as the source rules read it: the submission's Vietnam date (spec A-18). */
    private static SubmittedForm form(OwnerFormFacts facts) {
        return new SubmittedForm(SurveyTimeRules.vietnamDate(facts.submittedAt()), facts.addressDetail(),
                facts.provinceCode(), facts.wardCode(), facts.latitude(), facts.longitude(),
                facts.monthlyAskingPriceOrNull(), facts.availableFromOrNull());
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
