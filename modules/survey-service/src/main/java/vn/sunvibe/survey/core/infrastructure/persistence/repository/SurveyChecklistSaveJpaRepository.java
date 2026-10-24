package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistSaveEntity;

import java.util.List;

@Repository
public interface SurveyChecklistSaveJpaRepository extends JpaRepository<SurveyChecklistSaveEntity, Long> {

    // The Snowflake id grows with time, so it orders saves of the same instant
    List<SurveyChecklistSaveEntity> findByChecklistIdOrderBySavedAtAscIdAsc(String checklistId);
}
