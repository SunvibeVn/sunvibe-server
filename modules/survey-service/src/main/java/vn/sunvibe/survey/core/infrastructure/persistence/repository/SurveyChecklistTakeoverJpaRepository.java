package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistTakeoverEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyChecklistTakeoverJpaRepository extends JpaRepository<SurveyChecklistTakeoverEntity, Long> {

    List<SurveyChecklistTakeoverEntity> findByChecklistIdOrderByTakeoverNoAsc(String checklistId);

    Optional<SurveyChecklistTakeoverEntity> findByChecklistIdAndTakeoverNo(String checklistId, int takeoverNo);

    @Query("select coalesce(max(t.takeoverNo), 0) + 1 from SurveyChecklistTakeoverEntity t"
            + " where t.checklistId = :checklistId")
    int nextTakeoverNo(@Param("checklistId") String checklistId);
}
