package vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistSubmissionEntity;

@Repository
public interface SurveyChecklistSubmissionJpaRepository extends JpaRepository<SurveyChecklistSubmissionEntity, String> {
}
