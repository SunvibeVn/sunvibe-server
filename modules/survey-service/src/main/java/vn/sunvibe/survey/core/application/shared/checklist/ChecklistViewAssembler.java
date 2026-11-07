package vn.sunvibe.survey.core.application.shared.checklist;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistMediaView;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistReviewView;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistSummaryView;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistView;
import vn.sunvibe.survey.core.application.query.checklist.CorrectionView;
import vn.sunvibe.survey.core.application.query.checklist.EditorView;
import vn.sunvibe.survey.core.application.shared.common.ChecklistContextLoader;
import vn.sunvibe.survey.core.application.shared.common.RevisionPhotos;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.application.shared.media.MediaViewerRights;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSave;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistTakeover;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.checklist.SafetyContent;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.TermsContent;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ChecklistUnits;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupDocument;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.checklist.unit.ProvenanceUnit;
import vn.sunvibe.survey.core.domain.model.checklist.unit.UnitMeta;
import vn.sunvibe.survey.core.domain.policy.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistEditability;
import vn.sunvibe.survey.core.domain.policy.checklist.ChecklistHazards;
import vn.sunvibe.survey.core.domain.policy.checklist.EarlierHazard;
import vn.sunvibe.survey.core.domain.policy.checklist.EarlierHazardChain;
import vn.sunvibe.survey.core.domain.policy.checklist.EffectiveTerms;
import vn.sunvibe.survey.core.domain.policy.checklist.Editability;
import vn.sunvibe.survey.core.domain.policy.checklist.InputForm;
import vn.sunvibe.survey.core.domain.policy.checklist.LocationDifference;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.ReadinessCalculator;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionContext;
import vn.sunvibe.survey.core.domain.repository.media.IntakeHealthRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSaveDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistTakeoverDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds what a checklist answer shows, after the writes and outside the share lock's critical work (survey-checklist
 * spec 5.5, 7.2): the groups read in one statement, the viewer's editability, the address difference, readiness,
 * effective terms, the chain of earlier hazards, the owner's form, takeovers, corrections, editors and the lead's staff
 * for an editor (spec A-25); every staff name through one directory lookup. The evidence part of readiness and the
 * media block come from the media-set row and the photos read with the groups (survey-media spec 4.6, A-1): the two
 * read endpoints run this in one REPEATABLE READ transaction, so {@code missingCount} matches {@code missing[]}.
 */
@Component
public class ChecklistViewAssembler {

    private final SurveyChecklistDomainRepository checklists;
    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyChecklistTakeoverDomainRepository takeovers;
    private final SurveyChecklistSaveDomainRepository saves;
    private final SurveyLeadPort leads;
    private final SurveyorDirectoryPort directory;
    private final SurveyMediaSetDomainRepository mediaSets;
    private final RevisionPhotos revisionPhotos;
    private final MediaSwitches switches;
    private final ChecklistSwitches checklistSwitches;
    private final IntakeHealthRepository health;
    private final ChecklistReviewReader reviews;

    public ChecklistViewAssembler(SurveyChecklistDomainRepository checklists,
                                  SurveyChecklistGroupDomainRepository groups,
                                  SurveyChecklistTakeoverDomainRepository takeovers,
                                  SurveyChecklistSaveDomainRepository saves, SurveyLeadPort leads,
                                  SurveyorDirectoryPort directory, SurveyMediaSetDomainRepository mediaSets,
                                  RevisionPhotos revisionPhotos, MediaSwitches switches,
                                  ChecklistSwitches checklistSwitches, IntakeHealthRepository health,
                                  ChecklistReviewReader reviews) {
        this.checklists = checklists;
        this.groups = groups;
        this.takeovers = takeovers;
        this.saves = saves;
        this.leads = leads;
        this.directory = directory;
        this.mediaSets = mediaSets;
        this.revisionPhotos = revisionPhotos;
        this.switches = switches;
        this.checklistSwitches = checklistSwitches;
        this.health = health;
        this.reviews = reviews;
    }

    public ChecklistView assemble(SurveyViewer viewer, LeadFacts lead, List<SurveyAppointment> ofLead,
                                  SurveyChecklist checklist) {
        Map<ChecklistGroup, GroupState> states = groups.findAll(checklist.getId());
        IdentificationContent g1 = identification(states);
        SurveyAppointment visit = ChecklistContextLoader.visitOf(checklist, ofLead);
        boolean eligible = lead.assignedUserId() != null && directory.isEligible(lead.assignedUserId());
        Editability editability = editability(viewer, lead, ofLead, checklist, eligible);
        LocationDifference difference = difference(g1, visit);
        List<EarlierHazard> earlier = earlierHazards(checklist, states);
        Evidence evidence = evidence(checklist.getId());
        Readiness readiness = ReadinessCalculator.of(documents(states), difference, earlier, evidence.photosOrNull(),
                new SubmissionContext(switches.uploadsOn(), checklistSwitches.submissionEnabled(),
                        hasScheduled(ofLead)));
        ChecklistReviewView review = reviews.read(viewer, lead, ofLead, checklist, visit, states, readiness);
        ChecklistMediaView mediaView = new ChecklistMediaView(evidence.photosOrNull() != null,
                MediaViewerRights.uploadEnabled(switches.uploadsOn(), health.read(), editability),
                evidence.setVersion());
        TermsContent terms = (TermsContent) InputForm.ofStored(states.get(ChecklistGroup.TERMS).document(), g1);
        List<ChecklistTakeover> takeoverRows = takeovers.findByChecklistId(checklist.getId());
        List<ChecklistSave> saveRows = saves.findByChecklistId(checklist.getId());
        List<String> leadStaff = editability.editable() ? List.copyOf(leadStaff(lead, ofLead)) : List.of();
        List<ChecklistSave> uptoIt = savesOfTheVisit(checklist, saveRows);
        List<CorrectionView> corrections = uptoIt.stream().filter(save -> save.correctionOrNull() != null)
                .map(save -> new CorrectionView(String.valueOf(save.id()), save.group(),
                        save.correctionOrNull().units(), save.correctionOrNull().reason(),
                        save.correctionOrNull().basis(), save.actorUserId(), save.savedAt()))
                .toList();
        List<EditorView> editors = editors(saveRows);
        Instant updatedAt = checklist.getCreatedAt();
        for (ChecklistSave save : saveRows) {
            updatedAt = later(updatedAt, save.savedAt());
        }
        for (ChecklistTakeover takeover : takeoverRows) {
            updatedAt = later(updatedAt, takeover.occurredAt());
        }

        Set<String> ids = new LinkedHashSet<>(leadStaff);
        ids.add(checklist.getHolderUserId());
        ids.add(checklist.getCreatedBy());
        ids.add(visit.getVisit().performedBy());
        ids.add(visit.getVisit().recordedBy());
        addGroupIds(ids, states.values());
        takeoverRows.forEach(takeover -> {
            ids.add(takeover.fromUserId());
            ids.add(takeover.toUserId());
        });
        uptoIt.forEach(save -> ids.add(save.actorUserId()));
        earlier.forEach(hazard -> ids.add(hazard.hazard().receivedByUserId()));
        if (review.submissionOrNull() != null) {
            ids.add(review.submissionOrNull().submission().submittedByUserId());
        }
        if (review.decisionOrNull() != null) {
            ids.add(review.decisionOrNull().reviewerUserId());
        }
        if (review.requalificationOrNull() != null) {
            ids.add(review.requalificationOrNull().markedByUserId());
        }
        ids.remove(null);
        Map<String, String> names = ids.isEmpty() ? Map.of() : directory.displayNames(ids);
        return new ChecklistView(checklist, lead.requestCode(), lead.status(), leadStaff, visit, editability, states,
                difference, readiness, mediaView, EffectiveTerms.of(terms, g1.spaces()), earlier,
                leads.ownerForm(lead.leadId()).orElse(null), takeoverRows, corrections, editors, updatedAt, names,
                review);
    }

    /**
     * submission spec 4.6, U-5: a revision copies its groups with their correction pointers, so its read lists the saves
     * of the earlier revisions of the visit too; who edited it and when stay its own.
     */
    private List<ChecklistSave> savesOfTheVisit(SurveyChecklist checklist, List<ChecklistSave> own) {
        if (checklist.getPreviousChecklistIdOrNull() == null) {
            return own;
        }
        List<ChecklistSave> all = new ArrayList<>();
        checklists.findByLeadId(checklist.getLeadId()).stream()
                .filter(other -> other.getVisitAppointmentId().equals(checklist.getVisitAppointmentId())
                        && other.getChecklistNo() < checklist.getChecklistNo())
                .forEach(other -> all.addAll(saves.findByChecklistId(other.getId())));
        all.addAll(own);
        return all;
    }

    /**
     * The readiness of a revision from rows the caller read under its locks (submission spec 4.4, I-6): the gates of a
     * submit read the same lists the page shows.
     */
    public Readiness readiness(SurveyChecklist checklist, Map<ChecklistGroup, GroupState> states,
                               SurveyAppointment visit, Collection<SurveyMedia> photos, SubmissionContext context) {
        return ReadinessCalculator.of(documents(states), difference(identification(states), visit),
                earlierHazards(checklist, states), photos, context);
    }

    /** survey-checklist D3-7: a SCHEDULED appointment on the lead, whatever its cycle. */
    public static boolean hasScheduled(List<SurveyAppointment> ofLead) {
        return ofLead.stream().anyMatch(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED);
    }

    /**
     * The checklists of each VISITED appointment of the lead for the survey block, newest first (survey-checklist spec
     * 7.2 {@code checklists[]}): editability for the viewer, the number of missing items computed from the stored
     * groups and the latest group save. Groups of every checklist come in one statement.
     */
    public Map<String, List<ChecklistSummaryView>> summaries(SurveyViewer viewer, LeadFacts lead,
                                                             List<SurveyAppointment> ofLead, boolean assigneeEligible) {
        List<SurveyChecklist> ofLeadChecklists = checklists.findByLeadId(lead.leadId());
        if (ofLeadChecklists.isEmpty()) {
            return Map.of();
        }
        Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> allGroups = groups.findAllOf(
                ofLeadChecklists.stream().map(SurveyChecklist::getId).toList());
        Map<String, List<ChecklistSummaryView>> byVisit = new LinkedHashMap<>();
        ofLeadChecklists.stream().sorted(Comparator.comparingInt(SurveyChecklist::getChecklistNo).reversed())
                .forEach(checklist -> {
                    Map<ChecklistGroup, GroupState> states = allGroups.get(checklist.getId());
                    SurveyAppointment visit = ChecklistContextLoader.visitOf(checklist, ofLead);
                    Readiness readiness = ReadinessCalculator.of(documents(states),
                            difference(identification(states), visit), List.of(),
                            evidence(checklist.getId()).photosOrNull());
                    GroupState latest = states.values().stream().filter(state -> state.updatedAt() != null)
                            .max(Comparator.comparing(GroupState::updatedAt)).orElse(null);
                    byVisit.computeIfAbsent(checklist.getVisitAppointmentId().getValue(), id -> new ArrayList<>())
                            .add(new ChecklistSummaryView(checklist.getId().getValue(), checklist.getChecklistNo(),
                                    checklist.getStatus(), DecisionOutcome.ofStatus(checklist.getStatus()),
                                    editability(viewer, lead, ofLead, checklist, assigneeEligible),
                                    readiness.missingCount(), checklist.getHolderUserId(),
                                    latest == null ? checklist.getCreatedAt() : latest.updatedAt(),
                                    latest == null ? null : latest.updatedByUserId(),
                                    checklist.isRequalified()));
                });
        return byVisit;
    }

    /**
     * survey-media spec A-1: the photos are available when uploads are on or the checklist has a media-set row; then
     * every photo row of the checklist and the carried photos it selects, read after the set row (evidence of spec 4.6),
     * else null. Without a set row no photo exists (a photo is created under the set lock after the row).
     */
    private Evidence evidence(SurveyChecklistId checklistId) {
        Optional<MediaSet> set = mediaSets.findForRead(checklistId.getValue());
        if (set.isEmpty() && !switches.uploadsOn()) {
            return new Evidence(null, 0L);
        }
        List<SurveyMedia> photos = set.isEmpty() ? List.of() : revisionPhotos.of(checklistId.getValue());
        return new Evidence(photos, set.map(MediaSet::getSetVersion).orElse(0L));
    }

    private record Evidence(List<SurveyMedia> photosOrNull, long setVersion) {
    }

    /**
     * The lead's staff of survey-checklist BR-25, spec A-25: the current assignee, every user named on the lead's
     * History, and the performer and recorder of every appointment of the lead, computed from stored records before
     * any id of a request is read.
     */
    public Set<String> leadStaff(LeadFacts lead, List<SurveyAppointment> ofLead) {
        Set<String> staff = new LinkedHashSet<>(leads.leadStaffIds(lead.leadId()));
        for (SurveyAppointment appointment : ofLead) {
            if (appointment.getVisit() != null) {
                staff.add(appointment.getVisit().performedBy());
                staff.add(appointment.getVisit().recordedBy());
            }
        }
        staff.remove(null);
        return staff;
    }

    /** Every staff id a group's metadata and sources name (the names of the answer). */
    public static void addGroupIds(Set<String> ids, Collection<GroupState> states) {
        for (GroupState state : states) {
            ids.add(state.updatedByUserId());
            GroupDocument document = state.document();
            for (UnitMeta meta : document.meta().values()) {
                ids.add(meta.enteredByUserId());
                ids.add(meta.observedByUserId());
            }
            for (ProvenanceUnit unit : ChecklistUnits.of(document.content()).values()) {
                ids.add(unit.receivedByUserId());
                if (unit.ownerReportOrNull() != null) {
                    ids.add(unit.ownerReportOrNull().receivedByUserId());
                }
            }
        }
    }

    private Editability editability(SurveyViewer viewer, LeadFacts lead, List<SurveyAppointment> ofLead,
                                    SurveyChecklist checklist, boolean assigneeEligible) {
        Optional<String> currentVisit = CurrentVisitPolicy.currentVisit(ofLead, lead.intentCycle())
                .map(appointment -> appointment.getId().getValue());
        return ChecklistEditability.of(checklist.snapshot(), lead.status(), lead.intentCycle(),
                lead.assignedUserId(), assigneeEligible, currentVisit, viewer.userId());
    }

    /** survey-checklist BR-24, spec A-20: the checklists of the lead's cycle numbered below this one. */
    private List<EarlierHazard> earlierHazards(SurveyChecklist checklist, Map<ChecklistGroup, GroupState> states) {
        List<SurveyChecklist> earlier = checklists.findByLeadId(checklist.getLeadId()).stream()
                .filter(other -> other.getIntentCycle() == checklist.getIntentCycle()
                        && other.getChecklistNo() < checklist.getChecklistNo())
                .sorted(Comparator.comparingInt(SurveyChecklist::getChecklistNo))
                .toList();
        ChecklistHazards current = hazards(checklist, states);
        if (earlier.isEmpty()) {
            return EarlierHazardChain.of(List.of(), current);
        }
        Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> earlierGroups = groups.findAllOf(
                earlier.stream().map(SurveyChecklist::getId).toList());
        return EarlierHazardChain.of(earlier.stream()
                .map(other -> hazards(other, earlierGroups.get(other.getId()))).toList(), current);
    }

    public static ChecklistHazards hazards(SurveyChecklist checklist, Map<ChecklistGroup, GroupState> states) {
        SafetyContent safety = (SafetyContent) states.get(ChecklistGroup.SAFETY).document().content();
        List<Hazard> hazards = safety.hazards().isValue() ? safety.hazards().value() : List.of();
        return new ChecklistHazards(checklist.getId().getValue(), checklist.getVisitAppointmentId().getValue(),
                checklist.getChecklistNo(), hazards, checklist.isRequalified());
    }

    private static List<EditorView> editors(List<ChecklistSave> saves) {
        Map<String, Instant> latest = new LinkedHashMap<>();
        for (ChecklistSave save : saves) {
            latest.merge(save.actorUserId(), save.savedAt(), ChecklistViewAssembler::later);
        }
        return latest.entrySet().stream().map(entry -> new EditorView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(EditorView::lastSavedAt).reversed()).toList();
    }

    /** survey-checklist spec 4.6, BR-19: the observed address against the lead's place and the meeting place. */
    public static LocationDifference difference(IdentificationContent g1, SurveyAppointment visit) {
        return LocationDifference.of(g1.observedAddress().isValue() ? g1.observedAddress().value() : null,
                visit.getLeadLocation(), visit.getDetails().meetingLocation());
    }

    public static IdentificationContent identification(Map<ChecklistGroup, GroupState> states) {
        return (IdentificationContent) states.get(ChecklistGroup.IDENTIFICATION).document().content();
    }

    private static Map<ChecklistGroup, GroupDocument> documents(Map<ChecklistGroup, GroupState> states) {
        Map<ChecklistGroup, GroupDocument> documents = new EnumMap<>(ChecklistGroup.class);
        states.forEach((group, state) -> documents.put(group, state.document()));
        return documents;
    }

    private static Instant later(Instant left, Instant right) {
        return right.isAfter(left) ? right : left;
    }
}
