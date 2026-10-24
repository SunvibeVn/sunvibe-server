package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveySecurityEventEntity;

import java.util.List;

@Repository
public interface SurveySecurityEventJpaRepository extends JpaRepository<SurveySecurityEventEntity, Long> {

    List<SurveySecurityEventEntity> findByLeadIdOrderByOccurredAtAscIdAsc(String leadId);
}
