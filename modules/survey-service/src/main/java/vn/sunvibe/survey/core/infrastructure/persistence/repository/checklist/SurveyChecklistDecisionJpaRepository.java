package vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistDecisionEntity;

@Repository
public interface SurveyChecklistDecisionJpaRepository extends JpaRepository<SurveyChecklistDecisionEntity, String> {
}
