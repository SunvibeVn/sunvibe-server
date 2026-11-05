package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistRequalificationEntity;

@Repository
public interface SurveyChecklistRequalificationJpaRepository
        extends JpaRepository<SurveyChecklistRequalificationEntity, String> {
}
