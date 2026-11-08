package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.application.exception.checklist.ChecklistDecisionNotAllowedException;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistReviewReader;
import vn.sunvibe.survey.core.application.shared.checklist.ChecklistViewAssembler;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.media.MediaSelection;
import vn.sunvibe.survey.core.domain.model.media.PlacedMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.checklist.Readiness;
import vn.sunvibe.survey.core.domain.policy.checklist.SubmissionContext;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.domain.repository.common.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSelectionDomainRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The steps the checklist review commands share (survey-checklist submission spec 5.4): the selected photos locked in
 * the one lock order, the PASSED rules on the frozen rows, and the access-log row of the answer with the History entry.
 * Each runs in the transaction of the command that calls it.
 */
@Component
public class ChecklistReviewSteps {

    private final SurveyChecklistGroupDomainRepository groups;
    private final SurveyMediaDomainRepository media;
    private final SurveyMediaSetDomainRepository mediaSets;
    private final SurveyMediaSelectionDomainRepository selections;
    private final SurveyAccessLogDomainRepository accessLogs;
    private final SurveyLeadPort leads;
    private final ChecklistViewAssembler views;
    private final IdGenerator<Long> snowflakes;

    public ChecklistReviewSteps(SurveyChecklistGroupDomainRepository groups, SurveyMediaDomainRepository media,
                                SurveyMediaSetDomainRepository mediaSets,
                                SurveyMediaSelectionDomainRepository selections,
                                SurveyAccessLogDomainRepository accessLogs, SurveyLeadPort leads,
                                ChecklistViewAssembler views, @Qualifier("Snowflake") IdGenerator<Long> snowflakes) {
        this.groups = groups;
        this.media = media;
        this.mediaSets = mediaSets;
        this.selections = selections;
        this.accessLogs = accessLogs;
        this.leads = leads;
        this.views = views;
        this.snowflakes = snowflakes;
    }

    /** R2..R6 (survey-checklist submission spec 4.5) from the frozen G1 and G4 and the frozen photos as they stand now. */
    void requirePassable(SurveyChecklist checklist, SurveyAppointment visit, List<SurveyAppointment> ofLead,
                         List<EvidenceEntry> frozen, List<EvidenceRejection> rejections) {
        Map<ChecklistGroup, GroupState> states = groups.findAll(checklist.getId());
        List<SurveyMedia> photos = media.findByIds(frozen.stream().map(EvidenceEntry::mediaId).map(SurveyMediaId::of)
                .toList());
        Readiness readiness = views.readiness(checklist, states, visit, photos, SubmissionContext.CLOSED);
        List<String> blockers = ChecklistReviewReader.passedBlockers(readiness, states, photos, rejections.stream()
                .map(EvidenceRejection::mediaId).collect(Collectors.toSet()), ofLead);
        if (!blockers.isEmpty()) {
            throw new ChecklistDecisionNotAllowedException(blockers);
        }
    }

    /**
     * survey-checklist submission spec 5.4: the set rows of the revision and of its carried photos' origins in id order, then the selected photos
     * in id order. Ids come from scalar reads, so no photo is loaded before its lock.
     */
    List<SurveyMedia> lockSelectedPhotos(String checklistId) {
        List<MediaSelection> carried = selections.findByChecklistId(checklistId).stream()
                .filter(MediaSelection::isSelected).toList();
        Set<String> setIds = new TreeSet<>();
        setIds.add(checklistId);
        carried.forEach(selection -> setIds.add(selection.carriedFromChecklistId()));
        setIds.forEach(mediaSets::lockForUpdate);
        List<SurveyMediaId> ids = new ArrayList<>(media.findSelectedPlacements(checklistId).stream()
                .map(PlacedMedia::id).toList());
        carried.forEach(selection -> ids.add(selection.mediaId()));
        return media.lockForUpdate(ids);
    }

    /** The access-log row of the answer, then the History entry, the last write (survey-checklist submission spec 5.4). */
    ChecklistActivityResult finish(SurveyViewer viewer, LeadFacts lead, SurveyChecklist checklist,
                                   ChecklistActivityType type, Instant now) {
        logAccess(viewer, lead, checklist, now);
        return leads.recordChecklistActivity(lead.leadId(), new ChecklistActivity(type, viewer.userId(),
                checklist.getId().getValue(), checklist.getChecklistNo(), null, now));
    }

    /** The access-log row of the answer (survey-checklist submission spec 5.4). */
    void logAccess(SurveyViewer viewer, LeadFacts lead, SurveyChecklist checklist, Instant now) {
        accessLogs.save(SurveyAccessLog.create(snowflakes.generateId(), lead.leadId(), viewer.userId(), now,
                AccessResourceType.CHECKLIST, checklist.getId().getValue()));
    }
}
