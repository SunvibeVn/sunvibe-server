package vn.sunvibe.survey.core.application.shared.checklist;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.query.checklist.ChecklistReviewView;
import vn.sunvibe.survey.core.application.query.media.EvidenceView;
import vn.sunvibe.survey.core.application.query.common.PassedView;
import vn.sunvibe.survey.core.application.query.common.RefusedPhotoView;
import vn.sunvibe.survey.core.application.query.checklist.SubmissionView;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.staff.SurveyorDirectoryPort;
import vn.sunvibe.survey.core.application.shared.media.MediaSwitches;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.common.CurrentVisitPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.DecisionFacts;
import vn.sunvibe.survey.core.domain.policy.checklist.DecisionPolicy;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewReason;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewRights;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusion;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewerExclusionFacts;
import vn.sunvibe.survey.core.domain.policy.checklist.Usability;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistDecisionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistRequalificationDomainRepository;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistSubmissionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The review part of the reads (survey-checklist submission spec 4.2, 4.7, 7.2): the frozen facts of a revision, whether
 * the viewer can decide it and whether a PASSED one can still be used, on the rules the decision itself runs, and why it
 * was withdrawn (property-create requalification spec 4). With the refusals of the earlier revisions of the visit, it
 * flags each frozen photo that repeats a refused photo's content and lists the refused photos of the previous revision
 * that this one does not hold (survey-checklist SV-310); a revision without a previous revision costs no read of
 * earlier decisions.
 */
@Component
public class ChecklistReviewReader {

    private final SurveyChecklistSubmissionDomainRepository submissions;
    private final SurveyChecklistEvidenceDomainRepository evidence;
    private final SurveyChecklistDecisionDomainRepository decisions;
    private final SurveyChecklistRequalificationDomainRepository requalifications;
    private final SurveyMediaDomainRepository media;
    private final ReviewerExclusionLoader exclusions;
    private final SurveyRecordReader records;
    private final SurveyorDirectoryPort directory;
    private final MediaSwitches switches;

    public ChecklistReviewReader(SurveyChecklistSubmissionDomainRepository submissions,
                                 SurveyChecklistEvidenceDomainRepository evidence,
                                 SurveyChecklistDecisionDomainRepository decisions,
                                 SurveyChecklistRequalificationDomainRepository requalifications,
                                 SurveyMediaDomainRepository media, ReviewerExclusionLoader exclusions,
                                 SurveyRecordReader records,
                                 SurveyorDirectoryPort directory, MediaSwitches switches) {
        this.submissions = submissions;
        this.evidence = evidence;
        this.decisions = decisions;
        this.requalifications = requalifications;
        this.media = media;
        this.exclusions = exclusions;
        this.records = records;
        this.directory = directory;
        this.switches = switches;
    }

    /**
     * @param photosOrNull the photos the revision holds (its own and the carried ones it selects), as the caller read
     *                     them; null when photos are not available, so the revision holds none
     */
    public ChecklistReviewView read(SurveyViewer viewer, LeadFacts lead, List<SurveyAppointment> ofLead,
                                    SurveyChecklist checklist, SurveyAppointment visit,
                                    Map<ChecklistGroup, GroupState> states, Readiness readiness,
                                    Collection<SurveyMedia> photosOrNull) {
        SurveyChecklistId id = checklist.getId();
        ChecklistStatus status = checklist.getStatus();
        boolean frozen = status != ChecklistStatus.DRAFT;
        ChecklistDecision decision = status.isDecided() ? decisions.findByChecklistId(id).orElse(null) : null;
        List<EvidenceEntry> entries = frozen ? evidence.findByChecklistId(id) : List.of();
        Optional<ChecklistSubmission> submission = frozen ? submissions.findByChecklistId(id) : Optional.empty();
        Optional<ReviewReason> refusal = refusal(canActAsReviewer(viewer), checklist, lead.status(), lead.intentCycle(),
                ofLead, () -> exclusions.load(checklist, visit), lead.assignedUserId(), viewer.userId());
        EarlierRefusals earlier = refusalsBefore(checklist, ofLead);
        boolean flagged = frozen && !earlier.refusals().isEmpty();
        List<SurveyMedia> frozenPhotos = status == ChecklistStatus.SUBMITTED || flagged ? photosOf(entries) : List.of();
        List<String> blockedBy = status == ChecklistStatus.SUBMITTED
                ? passedBlockers(readiness, states, frozenPhotos, Set.of(), ofLead) : List.of();
        Map<String, List<RefusedPhotoView>> sameContent = flagged ? sameContent(earlier.refusals(), frozenPhotos)
                : Map.of();
        return new ChecklistReviewView(submission.map(row -> new SubmissionView(row, views(entries, decision,
                sameContent))).orElse(null), decision, new ReviewAccess(refusal.isEmpty(), refusal.orElse(null),
                blockedBy), status == ChecklistStatus.PASSED ? passed(checklist, lead) : null,
                checklist.isRequalified() ? requalifications.findByChecklistId(id).orElse(null) : null,
                notCarried(checklist, earlier, photosOrNull));
    }

    /** spec 4.2, plan PD-6: a reviewer role and an account that can sign in; both first in the command too. */
    public boolean canActAsReviewer(SurveyViewer viewer) {
        return !viewer.seesOwnLeadsOnly() && directory.canSignIn(viewer.userId());
    }

    /** The first reason the viewer cannot decide the revision; one path for the read and the queue. */
    public Optional<ReviewReason> refusal(boolean reviewerRole, SurveyChecklist checklist, LeadStatus leadStatus,
                                          int leadIntentCycle, List<SurveyAppointment> ofLead,
                                          Supplier<ReviewerExclusionFacts> facts, String assigneeUserId,
                                          String viewerUserId) {
        boolean onCurrentVisit = CurrentVisitPolicy.currentVisit(ofLead, leadIntentCycle)
                .filter(visit -> visit.getId().equals(checklist.getVisitAppointmentId())).isPresent();
        return ReviewRights.refusal(reviewerRole, DecisionPolicy.stateBlockers(checklist.getStatus(), leadStatus,
                        checklist.getIntentCycle(), leadIntentCycle, onCurrentVisit, switches.servingOn()),
                () -> ReviewerExclusion.excluded(facts.get(), assigneeUserId).contains(viewerUserId));
    }

    /** spec 4.7, from the submission's G1 and the frozen photos as they stand. */
    public PassedView passed(SurveyChecklist passed, LeadFacts lead) {
        Usability usability = records.usability(passed, lead.intentCycle(), lead.isClosed());
        ChecklistSubmission submission = submissions.findByChecklistId(passed.getId())
                .orElseThrow(() -> new IllegalStateException("A PASSED revision has its submission."));
        return new PassedView(usability.usable(), usability.reasonOrNull(), submission.rentalScope(),
                submission.spaces());
    }

    /** R2..R6 (spec 4.5) on the frozen G1 and G4 and the frozen photos as they stand; the decision runs the same. */
    public static List<String> passedBlockers(Readiness readiness, Map<ChecklistGroup, GroupState> states,
                                              Collection<SurveyMedia> frozen, Set<String> rejectedMediaIds,
                                              List<SurveyAppointment> ofLead) {
        return DecisionPolicy.passedBlockers(new DecisionFacts(readiness.warnings(), frozen, rejectedMediaIds,
                ChecklistViewAssembler.hasScheduled(ofLead), ChecklistViewAssembler.identification(states),
                (ConditionContent) states.get(ChecklistGroup.CONDITION).document().content()));
    }

    private List<SurveyMedia> photosOf(List<EvidenceEntry> entries) {
        return media.findByIds(entries.stream().map(EvidenceEntry::mediaId).map(SurveyMediaId::of).toList());
    }

    /** The refusals before a revision and the frozen entries read to place them, by revision. */
    private record EarlierRefusals(List<RefusedPhotoView> refusals,
                                   Map<SurveyChecklistId, List<EvidenceEntry>> entriesByRevision) {
    }

    /**
     * survey-checklist SV-310: the marks of the decisions on the earlier revisions of the visit, newest revision first
     * and each decision's marks in its order, placed by the refused photo's frozen entry in the revision that refused it.
     * A revision without a previous revision reads nothing; a mark without its entry is left out.
     */
    private EarlierRefusals refusalsBefore(SurveyChecklist checklist, List<SurveyAppointment> ofLead) {
        if (checklist.getPreviousChecklistIdOrNull() == null) {
            return new EarlierRefusals(List.of(), Map.of());
        }
        SurveyRecordReader.CycleRecord record = records.read(checklist.getLeadId(), checklist.getIntentCycle(), ofLead);
        List<RefusedPhotoView> refusals = new ArrayList<>();
        Map<SurveyChecklistId, List<EvidenceEntry>> entriesByRevision = new HashMap<>();
        for (SurveyChecklist earlier : record.decidedBefore(checklist)) {
            List<EvidenceRejection> marks = record.decisions().get(earlier.getId()).rejections();
            if (marks.isEmpty()) {
                continue;
            }
            List<EvidenceEntry> entries = evidence.findByChecklistId(earlier.getId());
            entriesByRevision.put(earlier.getId(), entries);
            Map<String, EvidenceEntry> byPhoto = entries.stream()
                    .collect(Collectors.toMap(EvidenceEntry::mediaId, entry -> entry, (first, second) -> first));
            for (EvidenceRejection mark : marks) {
                EvidenceEntry entry = byPhoto.get(mark.mediaId());
                if (entry != null) {
                    refusals.add(new RefusedPhotoView(mark.mediaId(), earlier.getId().getValue(),
                            earlier.getChecklistNo(), entry.category(), entry.spaceIdOrNull(), entry.spaceCodeOrNull(),
                            mark.reason()));
                }
            }
        }
        return new EarlierRefusals(refusals, entriesByRevision);
    }

    /**
     * survey-checklist SV-310: by frozen photo id, the refusals of a photo with the same source content (media A-16), in
     * the order of the refusals; a photo without a source hash matches nothing.
     */
    private Map<String, List<RefusedPhotoView>> sameContent(List<RefusedPhotoView> refusals,
                                                            Collection<SurveyMedia> frozenPhotos) {
        Map<String, String> refusedHashes = new HashMap<>();
        media.findByIds(refusals.stream().map(RefusedPhotoView::mediaId).distinct().map(SurveyMediaId::of).toList())
                .stream().filter(photo -> photo.getSourceSha256() != null)
                .forEach(photo -> refusedHashes.put(photo.getId().getValue(), photo.getSourceSha256()));
        Map<String, List<RefusedPhotoView>> byPhoto = new HashMap<>();
        for (SurveyMedia photo : frozenPhotos) {
            String hash = photo.getSourceSha256();
            if (hash == null) {
                continue;
            }
            List<RefusedPhotoView> same = refusals.stream()
                    .filter(refused -> hash.equals(refusedHashes.get(refused.mediaId()))).toList();
            if (!same.isEmpty()) {
                byPhoto.put(photo.getId().getValue(), same);
            }
        }
        return byPhoto;
    }

    /**
     * survey-checklist SV-310: the refused photos of the previous revision's frozen set that this revision does not hold,
     * one per photo with its newest refusal, newest revision first.
     */
    private List<RefusedPhotoView> notCarried(SurveyChecklist checklist, EarlierRefusals earlier,
                                              Collection<SurveyMedia> photosOrNull) {
        if (earlier.refusals().isEmpty()) {
            return List.of();
        }
        SurveyChecklistId previousId = checklist.getPreviousChecklistIdOrNull();
        List<EvidenceEntry> previousEntries = earlier.entriesByRevision().containsKey(previousId)
                ? earlier.entriesByRevision().get(previousId) : evidence.findByChecklistId(previousId);
        Set<String> previous = previousEntries.stream().map(EvidenceEntry::mediaId).collect(Collectors.toSet());
        Set<String> held = photosOrNull == null ? Set.of() : photosOrNull.stream()
                .map(photo -> photo.getId().getValue()).collect(Collectors.toSet());
        Map<String, RefusedPhotoView> newestPerPhoto = new LinkedHashMap<>();
        for (RefusedPhotoView refused : earlier.refusals()) {
            if (previous.contains(refused.mediaId()) && !held.contains(refused.mediaId())) {
                newestPerPhoto.putIfAbsent(refused.mediaId(), refused);
            }
        }
        return List.copyOf(newestPerPhoto.values());
    }

    private static List<EvidenceView> views(List<EvidenceEntry> entries, ChecklistDecision decisionOrNull,
                                            Map<String, List<RefusedPhotoView>> sameContent) {
        Map<String, String> rejected = decisionOrNull == null ? Map.of() : decisionOrNull.rejections().stream()
                .collect(Collectors.toMap(EvidenceRejection::mediaId, EvidenceRejection::reason));
        return entries.stream().map(entry -> new EvidenceView(entry,
                decisionOrNull == null ? null : !rejected.containsKey(entry.mediaId()),
                rejected.get(entry.mediaId()), sameContent.getOrDefault(entry.mediaId(), List.of()))).toList();
    }
}
