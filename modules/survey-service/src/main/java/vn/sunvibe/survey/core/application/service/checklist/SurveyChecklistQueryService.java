package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistView;
import vn.sunvibe.survey.core.application.query.checklist.CheckpointView;
import vn.sunvibe.survey.core.application.query.checklist.ReviewQueueItem;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistReviewReader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.checklist.ReviewerExclusionLoader;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadCycle;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistQueryUseCase;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewReason;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusionFacts;
import vn.sunvibe.survey.core.domain.repository.common.PageResult;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.appointment.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistTakeoverDomainRepository;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - checklist reads (survey-checklist spec 4.4, 4.10, A-10): for every reader of the lead; a
 * lead the caller cannot see answers not found and, when the checklist exists, writes a security event. Each read
 * writes one access-log row with the resource it showed.
 */
@Service
@Transactional
public class SurveyChecklistQueryService implements SurveyChecklistQueryUseCase {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final ChecklistViewAssembler views;
    private final ChecklistReviewReader reviews;
    private final ReviewerExclusionLoader exclusions;
    private final IdGenerator<Long> snowflakes;

    public SurveyChecklistQueryService(SurveyChecklistDomainRepository checklists,
                                       SurveyAppointmentDomainRepository appointments,
                                       SurveyChecklistTakeoverDomainRepository takeovers,
                                       SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                       SurveyorDirectoryPort directory, ChecklistContextLoader loader,
                                       ChecklistViewAssembler views, ChecklistReviewReader reviews,
                                       ReviewerExclusionLoader exclusions,
                                       @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.checklists = checklists;
        this.appointments = appointments;
        this.takeovers = takeovers;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.directory = directory;
        this.loader = loader;
        this.views = views;
        this.reviews = reviews;
        this.exclusions = exclusions;
        this.snowflakes = snowflakes;
    }

    /**
     * survey-media spec 4.6, 5.4 (plan PD-9): the groups, the media-set row and the photos in one REPEATABLE READ
     * snapshot, so missingCount matches missing[]; still read-write for the access-log row.
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ChecklistView read(SurveyViewer viewer, String checklistId) {
        ReadContext context = loader.loadForRead(viewer, checklistId, SecurityResourceType.CHECKLIST, checklistId);
        ChecklistView view = views.assemble(viewer, context.lead(), context.ofLead(), context.checklist());
        log(viewer, context, AccessResourceType.CHECKLIST, checklistId);
        return view;
    }

    /**
     * spec 4.10: a hidden lead writes a CHECKPOINT event whatever the number; an unknown number or an intake
     * declaration (no checkpoint) answers not found without one.
     */
    @Override
    public CheckpointView readCheckpoint(SurveyViewer viewer, String checklistId, int takeoverNo) {
        String resourceId = checklistId + ":" + takeoverNo;
        ReadContext context = loader.loadForRead(viewer, checklistId, SecurityResourceType.CHECKPOINT, resourceId);
        ChecklistTakeover takeover = takeovers.find(context.checklist().getId(), takeoverNo)
                .filter(row -> row.snapshotOrNull() != null)
                .orElseThrow(ChecklistNotFoundException::new);
        Set<String> ids = new LinkedHashSet<>();
        ids.add(takeover.fromUserId());
        ids.add(takeover.toUserId());
        ChecklistViewAssembler.addGroupIds(ids, takeover.snapshotOrNull().groups().values());
        ids.remove(null);
        Map<String, String> names = ids.isEmpty() ? Map.of() : directory.displayNames(ids);
        log(viewer, context, AccessResourceType.CHECKPOINT, resourceId);
        Map<ChecklistGroup, GroupState> groups = takeover.snapshotOrNull().groups();
        return new CheckpointView(takeover, groups, ChecklistViewAssembler.difference(
                ChecklistViewAssembler.identification(groups),
                ChecklistContextLoader.visitOf(context.checklist(), context.ofLead())), names);
    }

    /**
     * Plan PD-4: the viewer's live lead cycles from the lead queue, then one page of their SUBMITTED revisions. A row
     * costs no statement of its own: appointments, exclusion facts and names are each read once for the page, so
     * {@code canReview} here is the read's answer at a fixed cost (S-18). The list shows no record text and writes no
     * access log, as the survey list; opening a revision does.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReviewQueueItem> reviewQueue(SurveyViewer viewer, int page, int size) {
        List<LeadCycle> live = leads.visibleOpenLeadCycles(viewer);
        if (live.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0L);
        }
        PageResult<SurveyChecklist> found = checklists.findSubmittedOf(live.stream().map(LeadCycle::leadId).toList(),
                live.stream().map(LeadCycle::intentCycle).toList(), page, size);
        if (found.items().isEmpty()) {
            return new PageResult<>(List.of(), page, size, found.totalElements());
        }
        Map<String, LeadCycle> byLead = live.stream().collect(Collectors.toMap(LeadCycle::leadId, Function.identity()));
        Map<String, List<SurveyAppointment>> ofLeads = appointments.findByLeadIds(found.items().stream()
                .map(SurveyChecklist::getLeadId).distinct().toList()).stream()
                .collect(Collectors.groupingBy(SurveyAppointment::getLeadId));
        boolean reviewer = reviews.canActAsReviewer(viewer);
        Map<SurveyChecklistId, ReviewerExclusionFacts> facts = reviewer ? exclusions.loadAll(found.items().stream()
                .map(checklist -> new ReviewerExclusionLoader.Target(checklist, ChecklistContextLoader.visitOf(checklist,
                        ofLeads.get(checklist.getLeadId())))).toList()) : Map.of();
        Map<String, String> names = directory.displayNames(found.items().stream()
                .map(SurveyChecklist::getSubmittedByUserId).collect(Collectors.toSet()));
        List<ReviewQueueItem> items = found.items().stream().map(checklist -> {
            LeadCycle lead = byLead.get(checklist.getLeadId());
            Optional<ReviewReason> refusal = reviews.refusal(reviewer, checklist, lead.status(), lead.intentCycle(),
                    ofLeads.get(checklist.getLeadId()), () -> facts.get(checklist.getId()), lead.assignedUserIdOrNull(),
                    viewer.userId());
            return new ReviewQueueItem(checklist.getId().getValue(), checklist.getChecklistNo(), lead.leadId(),
                    lead.requestCode(), checklist.getSubmittedAt(), checklist.getSubmittedByUserId(),
                    names.get(checklist.getSubmittedByUserId()), refusal.isEmpty(), refusal.orElse(null));
        }).toList();
        return new PageResult<>(items, page, size, found.totalElements());
    }

    private void log(SurveyViewer viewer, ReadContext context, AccessResourceType type, String resourceId) {
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), context.lead().leadId(), viewer.userId(),
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS), type, resourceId));
    }
}
