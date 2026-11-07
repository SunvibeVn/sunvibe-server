package vn.sunvibe.survey.core.infrastructure.persistence.repository.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.media.SurveyMediaVariantEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.media.SurveyMediaVariantKey;

import java.util.Collection;
import java.util.List;

@Repository
public interface SurveyMediaVariantJpaRepository
        extends JpaRepository<SurveyMediaVariantEntity, SurveyMediaVariantKey> {

    List<SurveyMediaVariantEntity> findByIdMediaIdIn(Collection<String> mediaIds);
}
