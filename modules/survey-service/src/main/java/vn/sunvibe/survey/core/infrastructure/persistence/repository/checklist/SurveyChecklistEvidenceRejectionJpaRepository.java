package vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistEvidenceRejectionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.common.SurveyChecklistMediaKey;

import java.util.Collection;
import java.util.List;

@Repository
public interface SurveyChecklistEvidenceRejectionJpaRepository
        extends JpaRepository<SurveyChecklistEvidenceRejectionEntity, SurveyChecklistMediaKey> {

    List<SurveyChecklistEvidenceRejectionEntity> findByIdChecklistIdInOrderByIdMediaIdAsc(
            Collection<String> checklistIds);
}
