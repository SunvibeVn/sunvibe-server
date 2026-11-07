package vn.sunvibe.survey.core.infrastructure.persistence.adapter.checklist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklistId;
import vn.sunvibe.survey.core.domain.model.media.SurveyMediaId;
import vn.sunvibe.survey.core.domain.repository.checklist.SurveyChecklistEvidenceDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEvidenceEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.common.SurveyChecklistMediaKey;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist.SurveyChecklistEvidenceJpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Adapter - the insert-only frozen photo sets (survey-checklist submission spec 4.3, I-2, I-5). */
@Service
@RequiredArgsConstructor
public class SurveyChecklistEvidenceRepositoryAdapter implements SurveyChecklistEvidenceDomainRepository {

    private final SurveyChecklistEvidenceJpaRepository jpaRepository;

    @Override
    public void saveAll(SurveyChecklistId checklistId, List<EvidenceEntry> entries) {
        jpaRepository.saveAllAndFlush(entries.stream().map(entry -> SurveyChecklistEvidenceEntity.builder()
                .id(new SurveyChecklistMediaKey(checklistId.getValue(), entry.mediaId()))
                .category(entry.category())
                .spaceId(entry.spaceIdOrNull())
                .spaceCode(entry.spaceCodeOrNull())
                .fullSha256(entry.fullSha256())
                .build()).toList());
    }

    @Override
    public List<EvidenceEntry> findByChecklistId(SurveyChecklistId checklistId) {
        return jpaRepository.findByIdChecklistIdOrderByIdMediaIdAsc(checklistId.getValue()).stream()
                .map(row -> new EvidenceEntry(row.getId().getMediaId(), row.getCategory(), row.getSpaceId(),
                        row.getSpaceCode(), row.getFullSha256()))
                .toList();
    }

    @Override
    public Set<String> findReferencedAmong(Collection<SurveyMediaId> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jpaRepository.findMediaIdsReferenced(mediaIds.stream().map(SurveyMediaId::getValue)
                .toList()));
    }
}
