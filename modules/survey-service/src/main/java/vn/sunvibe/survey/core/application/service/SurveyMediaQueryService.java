package vn.sunvibe.survey.core.application.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.survey.core.application.exception.ChecklistNotFoundException;
import vn.sunvibe.survey.core.application.query.MediaListView;
import vn.sunvibe.survey.core.application.shared.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.MediaAccessLogger;
import vn.sunvibe.survey.core.application.shared.MediaItemAssembler;
import vn.sunvibe.survey.core.application.shared.MediaReadBudgets;
import vn.sunvibe.survey.core.application.shared.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.MediaViewerRights;
import vn.sunvibe.survey.core.application.shared.RevisionPhotos;
import vn.sunvibe.survey.core.application.shared.SecurityEventRecorder;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.usecase.SurveyMediaQueryUseCase;
import vn.sunvibe.survey.core.domain.exception.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.media.IntakeHealth;
import vn.sunvibe.survey.core.domain.model.media.MediaCounterKind;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistEditability;
import vn.sunvibe.survey.core.domain.policy.checklist.Editability;
import vn.sunvibe.survey.core.domain.policy.media.EvidenceCoverageCalculator;
import vn.sunvibe.survey.core.domain.policy.media.KnownVersion;
import vn.sunvibe.survey.core.domain.policy.media.MediaFileRules;
import vn.sunvibe.survey.core.domain.policy.media.MediaLimits;
import vn.sunvibe.survey.core.domain.repository.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyMediaSetDomainRepository;

/**
 * The media list (survey-media spec 4.3, the list order of 5.4, 7.2, A-6, A-26, A-30; plan PD-9): (1) a malformed
 * {@code knownVersion} 400 before any lookup, (2) the checklist and its lead visible (404, throttled event), (3) the
 * list budget in its own READ COMMITTED transaction, then one REPEATABLE READ, read-only snapshot of the set version,
 * the photos, the G1 and G4 versions coverage is computed from, the checklist version and the health row; 204 (empty)
 * only when all six components of the version equal the one sent; then the access row in its own transaction, a 204
 * included.
 */
@Service
public class SurveyMediaQueryService implements SurveyMediaQueryUseCase {

    /** spec 7.2: the newest not accepted and removed photos shown. */
    static final int NEWEST = 100;
    private static final Set<MediaStatus> NOT_ACCEPTED = EnumSet.of(MediaStatus.REJECTED, MediaStatus.FAILED,
            MediaStatus.CANCELLED, MediaStatus.UPLOAD_EXPIRED);

    private final ChecklistContextLoader loader;
    private final SurveyChecklistDomainRepository checklists;
    private final SurveyAppointmentDomainRepository appointments;
    private final SurveyChecklistGroupDomainRepository groups;
    private final RevisionPhotos revisionPhotos;
    private final SurveyMediaSetDomainRepository sets;
    private final IntakeHealthRepository health;
    private final SurveyorDirectoryPort directory;
    private final MediaSwitches switches;
    private final MediaItemAssembler items;
    private final MediaReadBudgets budgets;
    private final MediaAccessLogger accessLogger;
    private final SecurityEventRecorder events;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate snapshot;

    public SurveyMediaQueryService(ChecklistContextLoader loader, SurveyChecklistDomainRepository checklists,
                                   SurveyAppointmentDomainRepository appointments,
                                   SurveyChecklistGroupDomainRepository groups, RevisionPhotos revisionPhotos,
                                   SurveyMediaSetDomainRepository sets, IntakeHealthRepository health,
                                   SurveyorDirectoryPort directory, MediaSwitches switches,
                                   MediaItemAssembler items, MediaReadBudgets budgets, MediaAccessLogger accessLogger,
                                   SecurityEventRecorder events, PlatformTransactionManager transactionManager) {
        this.loader = loader;
        this.checklists = checklists;
        this.appointments = appointments;
        this.groups = groups;
        this.revisionPhotos = revisionPhotos;
        this.sets = sets;
        this.health = health;
        this.directory = directory;
        this.switches = switches;
        this.items = items;
        this.budgets = budgets;
        this.accessLogger = accessLogger;
        this.events = events;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.snapshot = new TransactionTemplate(transactionManager);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshot.setReadOnly(true);
    }

    @Override
    public Optional<MediaListView> list(SurveyViewer viewer, boolean viewerCanHold, String checklistId,
                                        String knownVersionOrNull) {
        KnownVersion known = null;
        if (knownVersionOrNull != null) {
            known = KnownVersion.parse(knownVersionOrNull).orElseThrow(() -> new ChecklistValidationException(
                    List.of(new Violation("knownVersion", "knownVersion is malformed."))));
        }
        LeadFacts lead = requireVisible(viewer, checklistId);
        budgets.count(viewer.userId(), lead.leadId(), MediaCounterKind.LIST, SecurityResourceType.MEDIA_SET,
                checklistId);
        return readLogged(viewer, viewerCanHold, checklistId, lead, known);
    }

    @Override
    public MediaListView listAfterDeselection(SurveyViewer viewer, boolean viewerCanHold, String checklistId,
                                              boolean changed) {
        LeadFacts lead = requireVisible(viewer, checklistId);
        if (!changed) {
            budgets.count(viewer.userId(), lead.leadId(), MediaCounterKind.LIST, SecurityResourceType.MEDIA_SET,
                    checklistId);
        }
        return readLogged(viewer, viewerCanHold, checklistId, lead, null)
                .orElseThrow(() -> new IllegalStateException("A list without a known version always answers."));
    }

    private LeadFacts requireVisible(SurveyViewer viewer, String checklistId) {
        try {
            return readTransaction.execute(status -> loader.requireMediaVisible(viewer, checklistId,
                    SecurityResourceType.MEDIA_SET, checklistId, SecurityAction.READ));
        } finally {
            events.flushDeferred();
        }
    }

    private Optional<MediaListView> readLogged(SurveyViewer viewer, boolean viewerCanHold, String checklistId,
                                               LeadFacts lead, KnownVersion sent) {
        Optional<MediaListView> view;
        try {
            view = snapshot.execute(status -> read(viewer, viewerCanHold, checklistId, sent));
        } finally {
            events.flushDeferred();
        }
        accessLogger.listRead(viewer.userId(), lead.leadId(), checklistId);
        return view;
    }

    private Optional<MediaListView> read(SurveyViewer viewer, boolean viewerCanHold, String checklistId,
                                         KnownVersion sent) {
        LeadFacts lead = loader.requireMediaVisible(viewer, checklistId, SecurityResourceType.MEDIA_SET,
                checklistId, SecurityAction.READ);
        SurveyChecklistId id = SurveyChecklistId.of(checklistId);
        SurveyChecklist checklist = checklists.findById(id).orElseThrow(ChecklistNotFoundException::new);
        List<SurveyAppointment> ofLead = appointments.findByLeadId(lead.leadId());
        Map<ChecklistGroup, GroupState> groupStates = groups.findAll(id);
        GroupState g1 = groupStates.get(ChecklistGroup.IDENTIFICATION);
        GroupState g4 = groupStates.get(ChecklistGroup.CONDITION);
        long setVersion = sets.findForRead(checklistId).map(MediaSet::getSetVersion).orElse(0L);
        // checklist submission spec 4.6: own and carried photos, read after the set row
        List<SurveyMedia> photos = revisionPhotos.of(checklistId);
        IntakeHealth row = health.read();
        boolean eligible = directory.isEligible(lead.assignedUserId());
        Optional<String> currentVisit = CurrentVisitPolicy.currentVisit(ofLead, lead.intentCycle())
                .map(visit -> visit.getId().getValue());
        Editability editability = ChecklistEditability.of(checklist.snapshot(), lead.status(), lead.intentCycle(),
                lead.assignedUserId(), eligible, currentVisit, viewer.userId());
        boolean uploadEnabled = MediaViewerRights.uploadEnabled(switches.uploadsOn(), row, editability);
        boolean serving = switches.servingOn();
        String digest = KnownVersion.viewDigest(editability.reason() == null ? null : editability.reason().name(),
                uploadEnabled, row.paused(), serving, viewerCanHold);
        KnownVersion basis = new KnownVersion(setVersion, g1.version(), g4.version(), checklist.getVersion(),
                row.stateEpoch(), digest);
        if (basis.equals(sent)) {
            return Optional.empty();
        }

        List<SurveyMedia> selected = photos.stream().filter(SurveyMedia::isSelected)
                .sorted(Comparator.comparingInt(SurveyMedia::getMediaNo)).toList();
        List<SurveyMedia> notAccepted = photos.stream().filter(photo -> !photo.isRemoved()
                && NOT_ACCEPTED.contains(photo.getStatus()))
                .sorted(Comparator.comparingInt(SurveyMedia::getMediaNo).reversed()).toList();
        List<SurveyMedia> removed = photos.stream().filter(SurveyMedia::isRemoved)
                .sorted(Comparator.comparing((SurveyMedia photo) -> photo.getRemoval().at())
                        .thenComparingInt(SurveyMedia::getMediaNo).reversed()).toList();
        int pending = (int) photos.stream().filter(SurveyMedia::isPending).count();
        MediaListView.Limits limits = new MediaListView.Limits(MediaFileRules.MAX_FILE_BYTES,
                MediaLimits.MAX_SELECTED, selected.size(), MediaLimits.MAX_PENDING, pending);
        ConditionContent g4Content = g4 == null ? null : (ConditionContent) g4.document().content();
        return Optional.of(new MediaListView(checklistId, basis, uploadEnabled, row.paused(), serving, viewerCanHold,
                limits, items.assemble(selected, viewerCanHold, revisionPhotos.carriedFrom(checklistId,
                        checklistNumbers(lead))),
                items.assemble(notAccepted.stream().limit(NEWEST).toList(), viewerCanHold),
                items.assemble(removed.stream().limit(NEWEST).toList(), viewerCanHold), notAccepted.size(),
                removed.size(), EvidenceCoverageCalculator.of((IdentificationContent) g1.document().content(),
                        g4Content, photos).pairs()));
    }

    private Map<String, Integer> checklistNumbers(LeadFacts lead) {
        return checklists.findByLeadId(lead.leadId()).stream().collect(Collectors.toMap(
                checklist -> checklist.getId().getValue(), SurveyChecklist::getChecklistNo));
    }
}
