package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.command.checklist.StartRevisionCommand;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotAllowedException;
import vn.sunvibe.survey.core.application.exception.common.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistRevisionResult;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistInputs.ReasonBasis;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistAlreadyStartedException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeoverRow;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistDateRules;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;
import vn.sunvibe.survey.core.domain.policy.media.RevisionSelection;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistTakeoverDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Starts the next revision of a visit returned for documents for {@link SurveyChecklistReviewCommandService}
 * (survey-checklist submission spec 4.6, 5.4, 5.5), in the service's transaction.
 */
@Component
public class ChecklistRevisionStarter {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyMediaSetDomainRepository mediaSets;
    private final SurveyMediaSelectionDomainRepository selections;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final SurveyRecordReader records;
    private final ChecklistViewAssembler views;
    private final ChecklistReviewSteps steps;
    private final IdGenerator<String> ulids;
    private final IdGenerator<Long> snowflakes;

    public ChecklistRevisionStarter(SurveyChecklistDomainRepository checklists,
                                    SurveyChecklistGroupDomainRepository groups,
                                    SurveyChecklistTakeoverDomainRepository takeovers,
                                    SurveyMediaSetDomainRepository mediaSets,
                                    SurveyMediaSelectionDomainRepository selections, SurveyorDirectoryPort directory,
                                    ChecklistContextLoader loader, SurveyRecordReader records,
                                    ChecklistViewAssembler views, ChecklistReviewSteps steps,
                                    @Qualifier("Ulid") IdGenerator<String> ulids,
                                    @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.checklists = checklists;
        this.groups = groups;
        this.takeovers = takeovers;
        this.mediaSets = mediaSets;
        this.selections = selections;
        this.directory = directory;
        this.loader = loader;
        this.records = records;
        this.views = views;
        this.steps = steps;
        this.ulids = ulids;
        this.snowflakes = snowflakes;
    }

    /**
     * Start revision check order (survey-checklist submission spec 5.5): body (400) → visible (404, event) → assignee (403, event) → state (409
     * CHECKLIST_NOT_ALLOWED on status) → a later revision (409 CHECKLIST_ALREADY_STARTED) → eligible (422) → intake
     * needed or absent (400). Locks (survey-checklist submission spec 5.4): lead and previous revision FOR SHARE, the new rows, then the origin sets
     * and photos. Groups keep content and per-unit metadata, so a copied value keeps who entered it (S-12). The photos a
     * decision on a revision of this visit marked not accepted stay behind (survey-checklist SV-211): they are locked
     * with the others, then left out of the carry.
     */
    ChecklistRevisionResult startRevision(SurveyViewer viewer, StartRevisionCommand command) {
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
        if (lead.status() != LeadStatus.SURVEY_SCHEDULED || previous.getIntentCycle() != lead.intentCycle()
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
            takeovers.save(ChecklistTakeoverRow.revisionByOther(snowflakes.generateId(), next.getId(),
                    previous.getHolderUserId(), viewer.userId(), intake.reason(), intake.basis(), now));
        }
        String nextId = next.getId().getValue();
        mediaSets.insertIgnoringExisting(nextId, now);
        List<SurveyMedia> selectedInPrevious = steps.lockSelectedPhotos(previous.getId().getValue());
        List<SurveyMedia> toCarry = RevisionSelection.photosToCarry(selectedInPrevious,
                record.rejectedOnVisit(previous.getVisitAppointmentId()));
        List<MediaSelection> carried = toCarry.stream()
                .map(photo -> MediaSelection.carried(nextId, photo.getId(), photo.getChecklistId(), now)).toList();
        if (!carried.isEmpty()) {
            selections.insertAll(carried);
        }
        ChecklistActivityResult activity = steps.finish(viewer, lead, next,
                ChecklistActivityType.CHECKLIST_REVISION_STARTED, now);
        return new ChecklistRevisionResult(views.assemble(viewer, lead, locked.ofLead(), next), activity.entry());
    }

    private static boolean returnedForDocuments(SurveyRecordReader.CycleRecord record, SurveyChecklist revision) {
        return record.mark(revision).needsChangesKindOrNull() == NeedsChangesKind.DOCUMENTS_ONLY;
    }
}
