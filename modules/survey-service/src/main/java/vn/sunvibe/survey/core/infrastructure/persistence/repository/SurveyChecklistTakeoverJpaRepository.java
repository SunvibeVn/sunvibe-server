package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistTakeoverEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyChecklistTakeoverJpaRepository extends JpaRepository<SurveyChecklistTakeoverEntity, Long> {

    List<SurveyChecklistTakeoverEntity> findByChecklistIdOrderByTakeoverNoAsc(String checklistId);

    @Query("select distinct new vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson(t.checklistId,"
            + " t.toUserId) from SurveyChecklistTakeoverEntity t where t.checklistId in :ids")
    List<ChecklistPerson> findTargets(@Param("ids") Collection<String> ids);

    Optional<SurveyChecklistTakeoverEntity> findByChecklistIdAndTakeoverNo(String checklistId, int takeoverNo);

    @Query("select coalesce(max(t.takeoverNo), 0) + 1 from SurveyChecklistTakeoverEntity t"
            + " where t.checklistId = :checklistId")
    int nextTakeoverNo(@Param("checklistId") String checklistId);
}
