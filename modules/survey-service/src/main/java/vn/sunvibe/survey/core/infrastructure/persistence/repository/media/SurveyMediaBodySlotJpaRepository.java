package vn.sunvibe.survey.core.infrastructure.persistence.repository.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.media.SurveyMediaBodySlotEntity;

import java.time.Instant;

@Repository
public interface SurveyMediaBodySlotJpaRepository extends JpaRepository<SurveyMediaBodySlotEntity, Long> {

    @Query("select count(s) from SurveyMediaBodySlotEntity s where s.userId = :userId and s.expiresAt > :now")
    long countLive(@Param("userId") String userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("delete from SurveyMediaBodySlotEntity s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
