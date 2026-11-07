package vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistRequalificationEntity;

@Repository
public interface SurveyChecklistRequalificationJpaRepository
        extends JpaRepository<SurveyChecklistRequalificationEntity, String> {
}
