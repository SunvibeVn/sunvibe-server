package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistGroupEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistGroupKey;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyChecklistGroupJpaRepository
        extends JpaRepository<SurveyChecklistGroupEntity, SurveyChecklistGroupKey> {

    List<SurveyChecklistGroupEntity> findByIdChecklistId(String checklistId);

    List<SurveyChecklistGroupEntity> findByIdChecklistIdIn(Collection<String> checklistIds);

    List<SurveyChecklistGroupEntity> findByIdChecklistIdAndIdGroupKeyNot(String checklistId, ChecklistGroup except);

    /** The row with a PostgreSQL {@code FOR SHARE} lock (survey-checklist spec 5.5). */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select g from SurveyChecklistGroupEntity g where g.id = :id")
    Optional<SurveyChecklistGroupEntity> findForShareById(@Param("id") SurveyChecklistGroupKey id);

    /** The row with a PostgreSQL {@code FOR UPDATE} lock (survey-checklist spec 5.5). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SurveyChecklistGroupEntity g where g.id = :id")
    Optional<SurveyChecklistGroupEntity> findForUpdateById(@Param("id") SurveyChecklistGroupKey id);
}
