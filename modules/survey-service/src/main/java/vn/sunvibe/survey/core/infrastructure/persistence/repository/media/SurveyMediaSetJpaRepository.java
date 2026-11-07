package vn.sunvibe.survey.core.infrastructure.persistence.repository.media;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.support.MediaSql;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.media.SurveyMediaSetEntity;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface SurveyMediaSetJpaRepository extends JpaRepository<SurveyMediaSetEntity, String> {

    /** The row with a PostgreSQL {@code FOR UPDATE} lock (survey-media spec 5.4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SurveyMediaSetEntity s where s.checklistId = :checklistId")
    Optional<SurveyMediaSetEntity> findForUpdateById(@Param("checklistId") String checklistId);

    @Modifying(flushAutomatically = true)
    @Query(value = MediaSql.INSERT_SET_IGNORING_EXISTING, nativeQuery = true)
    int insertIgnoringExisting(@Param("checklistId") String checklistId, @Param("now") Instant now);
}
