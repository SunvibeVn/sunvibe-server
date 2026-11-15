package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.repository.checklist.SourceStandingRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEvidenceEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistRequalificationEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistEvidenceJpaRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistJpaRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistRequalificationJpaRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapter - the set reads of the source standing, with the row mappings of the single-checklist adapters. */
@Service
@RequiredArgsConstructor
public class SourceStandingRepositoryAdapter implements SourceStandingRepository {

    private final SurveyChecklistJpaRepository checklists;
    private final SurveyChecklistEvidenceJpaRepository evidence;
    private final SurveyChecklistRequalificationJpaRepository requalifications;

    @Override
    public List<SurveyChecklist> findChecklistsOfLeads(Collection<String> leadIds) {
        if (leadIds.isEmpty()) {
            return List.of();
        }
        return checklists.findByLeadIdInOrderByLeadIdAscChecklistNoAsc(leadIds).stream()
                .map(SurveyChecklistRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Map<SurveyChecklistId, List<EvidenceEntry>> findEvidenceOf(Collection<SurveyChecklistId> checklistIds) {
        if (checklistIds.isEmpty()) {
            return Map.of();
        }
        Map<SurveyChecklistId, List<EvidenceEntry>> byChecklist = new HashMap<>();
        for (SurveyChecklistEvidenceEntity row : evidence.findByIdChecklistIdInOrderByIdChecklistIdAscIdMediaIdAsc(
                values(checklistIds))) {
            byChecklist.computeIfAbsent(SurveyChecklistId.of(row.getId().getChecklistId()), id -> new ArrayList<>())
                    .add(SurveyChecklistEvidenceRepositoryAdapter.toDomain(row));
        }
        return byChecklist;
    }

    @Override
    public Map<SurveyChecklistId, ChecklistRequalification> findRequalificationsOf(
            Collection<SurveyChecklistId> checklistIds) {
        if (checklistIds.isEmpty()) {
            return Map.of();
        }
        Map<SurveyChecklistId, ChecklistRequalification> byChecklist = new HashMap<>();
        for (SurveyChecklistRequalificationEntity row : requalifications.findAllById(values(checklistIds))) {
            SurveyChecklistId id = SurveyChecklistId.of(row.getChecklistId());
            byChecklist.put(id, SurveyChecklistRequalificationRepositoryAdapter.toDomain(id, row));
        }
        return byChecklist;
    }

    private static List<String> values(Collection<SurveyChecklistId> checklistIds) {
        return checklistIds.stream().map(SurveyChecklistId::getValue).distinct().toList();
    }
}
