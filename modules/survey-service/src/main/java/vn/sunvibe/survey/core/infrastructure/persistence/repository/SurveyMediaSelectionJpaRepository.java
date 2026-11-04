package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyChecklistMediaKey;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaSelectionEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyMediaSelectionJpaRepository
        extends JpaRepository<SurveyMediaSelectionEntity, SurveyChecklistMediaKey> {

    List<SurveyMediaSelectionEntity> findByIdChecklistIdInOrderByIdMediaIdAsc(Collection<String> checklistIds);

    @Query("select s.carriedFromChecklistId from SurveyMediaSelectionEntity s where s.id.checklistId = :checklistId"
            + " and s.id.mediaId = :mediaId")
    Optional<String> findCarriedFrom(@Param("checklistId") String checklistId, @Param("mediaId") String mediaId);

    @Query("select s.id.checklistId from SurveyMediaSelectionEntity s where s.id.mediaId = :mediaId"
            + " and s.deselectedAt is null order by s.id.checklistId")
    List<String> findSelectingChecklistIds(@Param("mediaId") String mediaId);
}
