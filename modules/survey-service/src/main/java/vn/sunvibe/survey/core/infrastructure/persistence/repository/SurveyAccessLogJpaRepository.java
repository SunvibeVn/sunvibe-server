package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAccessLogEntity;

import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by SurveyAccessLogRepositoryAdapter.
 */
@Repository
public interface SurveyAccessLogJpaRepository extends JpaRepository<SurveyAccessLogEntity, Long> {

    List<SurveyAccessLogEntity> findByLeadIdOrderByAccessedAtAscIdAsc(String leadId);
}
