package vn.sunvibe.survey.core.application.service.checklist;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.survey.core.application.shared.common.SurveyRecordReader;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingView;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.policy.checklist.PassedUsability;
import vn.sunvibe.survey.core.domain.policy.checklist.Usability;
import vn.sunvibe.survey.core.domain.repository.checklist.SourceStandingRepository;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaDomainRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use Case Implementation - read 2 of ADR-012 decision 2. Per chunk of 250 leads, in the caller's order: the checklists of
 * the leads, the frozen evidence of their current PASSED revisions, the media of those photos (with their variants), and
 * the requalification rows of the marked revisions. The current PASSED is {@link SurveyRecordReader.CycleRecord#passed()}
 * and its usability {@link PassedUsability#of} with the lead taken as open, as {@link SurveyIntakeSourceService} reads them.
 */
@Service
public class SourceStandingService implements SourceStandingUseCase {

    private static final int CHUNK = 250;

    private final SourceStandingRepository rows;
    private final SurveyMediaDomainRepository media;

    public SourceStandingService(SourceStandingRepository rows, SurveyMediaDomainRepository media) {
        this.rows = rows;
        this.media = media;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, SourceStandingView> standings(Map<String, Integer> intentCycleByLead) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        intentCycleByLead.forEach((leadId, cycle) -> entries.add(Map.entry(leadId, cycle)));
        Map<String, SourceStandingView> answered = new HashMap<>();
        for (int from = 0; from < entries.size(); from += CHUNK) {
            Map<String, Integer> chunk = new LinkedHashMap<>();
            entries.subList(from, Math.min(from + CHUNK, entries.size()))
                    .forEach(entry -> chunk.put(entry.getKey(), entry.getValue()));
            answered.putAll(chunk(chunk));
        }
        return Map.copyOf(answered);
    }

    private Map<String, SourceStandingView> chunk(Map<String, Integer> cycles) {
        List<SurveyChecklist> checklists = rows.findChecklistsOfLeads(cycles.keySet());
        Map<String, List<SurveyChecklist>> byLead = checklists.stream()
                .collect(Collectors.groupingBy(SurveyChecklist::getLeadId));
        Map<String, SurveyChecklist> passedByLead = new HashMap<>();
        cycles.forEach((leadId, cycle) -> {
            List<SurveyChecklist> ofCycle = byLead.getOrDefault(leadId, List.of()).stream()
                    .filter(checklist -> checklist.getIntentCycle() == cycle).toList();
            new SurveyRecordReader.CycleRecord(ofCycle, Map.of(), Optional.empty()).passed()
                    .ifPresent(passed -> passedByLead.put(leadId, passed));
        });
        Map<SurveyChecklistId, List<EvidenceEntry>> evidence = rows.findEvidenceOf(passedByLead.values().stream()
                .map(SurveyChecklist::getId).toList());
        Map<String, SurveyMedia> mediaById = media.findByIds(evidence.values().stream().flatMap(List::stream)
                        .map(EvidenceEntry::mediaId).distinct().map(SurveyMediaId::of).toList()).stream()
                .collect(Collectors.toMap(photo -> photo.getId().getValue(), Function.identity()));
        Map<SurveyChecklistId, ChecklistRequalification> requalifications = rows.findRequalificationsOf(checklists.stream()
                .filter(SurveyChecklist::isRequalified).map(SurveyChecklist::getId).toList());
        Map<String, SourceStandingView> answered = new HashMap<>();
        cycles.forEach((leadId, cycle) -> {
            List<IntakeSourceView.MarkedRevision> marks = marks(byLead.getOrDefault(leadId, List.of()), requalifications);
            SurveyChecklist passed = passedByLead.get(leadId);
            if (passed == null) {
                answered.put(leadId, new SourceStandingView(null, false, null, marks));
                return;
            }
            List<SurveyMedia> frozen = evidence.getOrDefault(passed.getId(), List.of()).stream()
                    .map(entry -> mediaById.get(entry.mediaId())).filter(Objects::nonNull).toList();
            Usability usability = PassedUsability.of(passed.isRequalified(), passed.getIntentCycle(), cycle, false, frozen);
            answered.put(leadId, new SourceStandingView(passed.getId().getValue(), usability.usable(),
                    usability.reasonOrNull() == null ? null : usability.reasonOrNull().name(), marks));
        });
        return answered;
    }

    /** As {@link SurveyIntakeSourceService#markedRevisions}: the marked revisions of any cycle, by number. */
    private static List<IntakeSourceView.MarkedRevision> marks(List<SurveyChecklist> ofLead,
                                                              Map<SurveyChecklistId, ChecklistRequalification> requalifications) {
        return ofLead.stream().filter(SurveyChecklist::isRequalified)
                .map(checklist -> new IntakeSourceView.MarkedRevision(checklist.getId().getValue(),
                        Optional.ofNullable(requalifications.get(checklist.getId()))
                                .map(ChecklistRequalification::kind).map(Enum::name).orElse(null),
                        checklist.getRequalifiedAt()))
                .toList();
    }
}
