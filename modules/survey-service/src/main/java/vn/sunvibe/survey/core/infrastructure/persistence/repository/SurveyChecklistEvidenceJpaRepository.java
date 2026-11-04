package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistEvidenceEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistMediaKey;

import java.util.Collection;
import java.util.List;

@Repository
public interface SurveyChecklistEvidenceJpaRepository
        extends JpaRepository<SurveyChecklistEvidenceEntity, SurveyChecklistMediaKey> {

    List<SurveyChecklistEvidenceEntity> findByIdChecklistIdOrderByIdMediaIdAsc(String checklistId);

    @Query("select distinct e.id.mediaId from SurveyChecklistEvidenceEntity e where e.id.mediaId in :mediaIds")
    List<String> findMediaIdsReferenced(@Param("mediaIds") Collection<String> mediaIds);
}
