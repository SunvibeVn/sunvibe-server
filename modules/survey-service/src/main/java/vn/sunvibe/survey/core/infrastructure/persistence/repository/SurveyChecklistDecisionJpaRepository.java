package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistDecisionEntity;

@Repository
public interface SurveyChecklistDecisionJpaRepository extends JpaRepository<SurveyChecklistDecisionEntity, String> {
}
