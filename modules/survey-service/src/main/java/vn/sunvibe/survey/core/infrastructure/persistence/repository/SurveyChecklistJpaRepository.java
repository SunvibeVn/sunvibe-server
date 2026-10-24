package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyChecklistJpaRepository extends JpaRepository<SurveyChecklistEntity, String> {

    /** The lead of a checklist without loading it (survey-checklist spec 5.5). */
    @Query("select c.leadId from SurveyChecklistEntity c where c.id = :id")
    Optional<String> findLeadIdById(@Param("id") String id);

    List<SurveyChecklistEntity> findByLeadIdOrderByChecklistNoAsc(String leadId);

    @Query("select coalesce(max(c.checklistNo), 0) + 1 from SurveyChecklistEntity c where c.leadId = :leadId")
    int nextChecklistNo(@Param("leadId") String leadId);
}
