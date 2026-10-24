package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.checklist.unit.GroupState;
import vn.sunvibe.survey.core.domain.repository.SurveyChecklistGroupDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistGroupEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistGroupKey;
import vn.sunvibe.survey.core.infrastructure.persistence.json.ChecklistDocumentCodec;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyChecklistGroupJpaRepository;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter - checklist group rows on JPA with the JSON codec. The locking reads map the entity their own select returned
 * (survey-checklist spec 5.5); an update names the version the save read, so a row changed since fails at the flush
 * with an optimistic locking failure.
 */
@Service
@RequiredArgsConstructor
public class SurveyChecklistGroupRepositoryAdapter implements SurveyChecklistGroupDomainRepository {

    private final SurveyChecklistGroupJpaRepository jpaRepository;
    private final ChecklistDocumentCodec codec;

    @Override
    public void insertAll(SurveyChecklistId checklistId, Map<ChecklistGroup, GroupState> groups) {
        jpaRepository.saveAllAndFlush(groups.entrySet().stream()
                .map(entry -> SurveyChecklistGroupEntity.builder()
                        .id(new SurveyChecklistGroupKey(checklistId.getValue(), entry.getKey()))
                        .content(codec.write(entry.getValue().document()))
                        .updatedBy(entry.getValue().updatedByUserId())
                        .updatedAt(entry.getValue().updatedAt())
                        .build())
                .toList());
    }

    @Override
    public Map<ChecklistGroup, GroupState> findAll(SurveyChecklistId checklistId) {
        return byGroup(jpaRepository.findByIdChecklistId(checklistId.getValue()));
    }

    @Override
    public Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> findAllOf(
            Collection<SurveyChecklistId> checklistIds) {
        if (checklistIds.isEmpty()) {
            return Map.of();
        }
        Map<SurveyChecklistId, List<SurveyChecklistGroupEntity>> rows = new LinkedHashMap<>();
        for (SurveyChecklistGroupEntity row : jpaRepository.findByIdChecklistIdIn(
                checklistIds.stream().map(SurveyChecklistId::getValue).toList())) {
            rows.computeIfAbsent(SurveyChecklistId.of(row.getId().getChecklistId()), id -> new java.util.ArrayList<>())
                    .add(row);
        }
        Map<SurveyChecklistId, Map<ChecklistGroup, GroupState>> groups = new LinkedHashMap<>();
        rows.forEach((id, list) -> groups.put(id, byGroup(list)));
        return groups;
    }

    @Override
    public GroupState lockForShare(SurveyChecklistId checklistId, ChecklistGroup group) {
        return state(jpaRepository.findForShareById(new SurveyChecklistGroupKey(checklistId.getValue(), group))
                .orElseThrow(() -> new IllegalStateException("A checklist has all its group rows.")));
    }

    @Override
    public GroupState lockForUpdate(SurveyChecklistId checklistId, ChecklistGroup group) {
        return state(jpaRepository.findForUpdateById(new SurveyChecklistGroupKey(checklistId.getValue(), group))
                .orElseThrow(() -> new IllegalStateException("A checklist has all its group rows.")));
    }

    @Override
    public Map<ChecklistGroup, GroupState> findOthers(SurveyChecklistId checklistId, ChecklistGroup except) {
        return byGroup(jpaRepository.findByIdChecklistIdAndIdGroupKeyNot(checklistId.getValue(), except));
    }

    @Override
    public GroupState update(SurveyChecklistId checklistId, ChecklistGroup group, long expectedVersion,
                             GroupState next) {
        SurveyChecklistGroupEntity saved = jpaRepository.saveAndFlush(SurveyChecklistGroupEntity.builder()
                .id(new SurveyChecklistGroupKey(checklistId.getValue(), group))
                .content(codec.write(next.document()))
                .version(expectedVersion)
                .updatedBy(next.updatedByUserId())
                .updatedAt(next.updatedAt())
                .build());
        return new GroupState(next.document(), saved.getVersion(), next.updatedAt(), next.updatedByUserId());
    }

    private Map<ChecklistGroup, GroupState> byGroup(List<SurveyChecklistGroupEntity> rows) {
        Map<ChecklistGroup, GroupState> groups = new EnumMap<>(ChecklistGroup.class);
        for (SurveyChecklistGroupEntity row : rows) {
            groups.put(row.getId().getGroupKey(), state(row));
        }
        return groups;
    }

    private GroupState state(SurveyChecklistGroupEntity row) {
        return new GroupState(codec.read(row.getId().getGroupKey(), row.getContent()), row.getVersion(),
                row.getUpdatedAt(), row.getUpdatedBy());
    }
}
