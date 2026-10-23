package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyResumptionEntity;

import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by SurveyResumptionRepositoryAdapter.
 */
@Repository
public interface SurveyResumptionJpaRepository extends JpaRepository<SurveyResumptionEntity, Long> {

    List<SurveyResumptionEntity> findByLeadIdOrderByOccurredAtAscIdAsc(String leadId);
}
