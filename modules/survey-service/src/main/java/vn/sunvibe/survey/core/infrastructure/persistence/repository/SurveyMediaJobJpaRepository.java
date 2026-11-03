package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.MediaSql;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaJobEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface SurveyMediaJobJpaRepository extends JpaRepository<SurveyMediaJobEntity, Long> {

    List<SurveyMediaJobEntity> findByMediaIdOrderByKindAsc(String mediaId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = MediaSql.UPSERT_JOB, nativeQuery = true)
    int upsert(@Param("mediaId") String mediaId, @Param("kind") String kind, @Param("dueAt") Instant dueAt,
               @Param("now") Instant now);
}
