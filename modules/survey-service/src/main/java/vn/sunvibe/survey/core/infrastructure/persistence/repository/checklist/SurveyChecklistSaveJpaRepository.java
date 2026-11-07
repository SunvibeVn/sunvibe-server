package vn.sunvibe.survey.core.infrastructure.persistence.repository.checklist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist.SurveyChecklistSaveEntity;

import java.util.Collection;
import java.util.List;

@Repository
public interface SurveyChecklistSaveJpaRepository extends JpaRepository<SurveyChecklistSaveEntity, Long> {

    // The Snowflake id grows with time, so it orders saves of the same instant
    List<SurveyChecklistSaveEntity> findByChecklistIdOrderBySavedAtAscIdAsc(String checklistId);

    @Query("select distinct new vn.sunvibe.survey.core.domain.model.checklist.ChecklistPerson(s.checklistId,"
            + " s.actorUserId) from SurveyChecklistSaveEntity s where s.checklistId in :ids")
    List<ChecklistPerson> findActors(@Param("ids") Collection<String> ids);
}
