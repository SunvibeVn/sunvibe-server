package vn.sunvibe.survey.core.infrastructure.persistence.adapter.media;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.model.media.MediaSet;
import vn.sunvibe.survey.core.domain.repository.media.SurveyMediaSetDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.media.SurveyMediaSetEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.media.SurveyMediaSetJpaRepository;

import java.time.Instant;
import java.util.Optional;

/** Adapter - the media-set row of a checklist (survey-media spec 5.2, 5.4). */
@Service
@RequiredArgsConstructor
public class SurveyMediaSetRepositoryAdapter implements SurveyMediaSetDomainRepository {

    private final SurveyMediaSetJpaRepository jpaRepository;

    @Override
    public void insertIgnoringExisting(String checklistId, Instant now) {
        jpaRepository.insertIgnoringExisting(checklistId, now);
    }

    @Override
    public Optional<MediaSet> lockForUpdate(String checklistId) {
        return jpaRepository.findForUpdateById(checklistId).map(SurveyMediaSetRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<MediaSet> findForRead(String checklistId) {
        return jpaRepository.findById(checklistId).map(SurveyMediaSetRepositoryAdapter::toDomain);
    }

    @Override
    public void update(MediaSet set) {
        jpaRepository.saveAndFlush(SurveyMediaSetEntity.builder()
                .checklistId(set.getChecklistId())
                .setVersion(set.getSetVersion())
                .lastMediaNo(set.getLastMediaNo())
                .updatedAt(set.getUpdatedAt())
                .build());
    }

    private static MediaSet toDomain(SurveyMediaSetEntity entity) {
        return MediaSet.load(entity.getChecklistId(), entity.getSetVersion(), entity.getLastMediaNo(),
                entity.getUpdatedAt());
    }
}
