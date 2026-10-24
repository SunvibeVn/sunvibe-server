package vn.sunvibe.survey.core.application.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.time.AppClock;
import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.query.ChecklistView;
import vn.sunvibe.survey.core.application.query.CheckpointView;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader.ReadContext;
import vn.sunvibe.survey.core.application.shared.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.SurveyChecklistQueryUseCase;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistTakeoverDomainRepository;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Use Case Implementation - checklist reads (survey-checklist spec 4.4, 4.10, A-10): for every reader of the lead; a
 * lead the caller cannot see answers not found and, when the checklist exists, writes a security event. Each read
 * writes one access-log row with the resource it showed.
 */
@Service
@Transactional
public class SurveyChecklistQueryService implements SurveyChecklistQueryUseCase {

    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyorDirectoryPort directory;
    private final ChecklistContextLoader loader;
    private final ChecklistViewAssembler views;
    private final IdGenerator<Long> snowflakes;

    public SurveyChecklistQueryService(SurveyChecklistTakeoverDomainRepository takeovers,
                                       SurveyAccessLogDomainRepository accessLogs, SurveyorDirectoryPort directory,
                                       ChecklistContextLoader loader, ChecklistViewAssembler views,
                                       @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.takeovers = takeovers;
        this.accessLogs = accessLogs;
        this.directory = directory;
        this.loader = loader;
        this.views = views;
        this.snowflakes = snowflakes;
    }

    @Override
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

    private void log(SurveyViewer viewer, ReadContext context, AccessResourceType type, String resourceId) {
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), context.lead().leadId(), viewer.userId(),
                AppClock.currentUTC().truncatedTo(ChronoUnit.MICROS), type, resourceId));
    }
}
