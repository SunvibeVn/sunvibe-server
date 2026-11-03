package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.domain.model.media.MediaStatus;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.MediaSql;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyMediaEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyMediaJpaRepository extends JpaRepository<SurveyMediaEntity, String> {

    List<SurveyMediaEntity> findByChecklistIdOrderByMediaNoAsc(String checklistId);

    /** The rows with a PostgreSQL row lock, taken in id order (survey-media spec 5.4). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from SurveyMediaEntity m where m.id in :ids order by m.id")
    List<SurveyMediaEntity> findForUpdateByIdIn(@Param("ids") Collection<String> ids);

    @Query("select m.id, m.spaceId, m.category from SurveyMediaEntity m where m.checklistId = :checklistId"
            + " and m.removedAt is null and m.status in :statuses order by m.id")
    List<Object[]> findPlacementsOfChecklist(@Param("checklistId") String checklistId,
                                             @Param("statuses") Collection<MediaStatus> statuses);

    @Query("select m.id from SurveyMediaEntity m where m.checklistId = :checklistId"
            + " and m.uploadedByUserId = :uploader and m.status in :statuses order by m.id")
    List<String> findIdsOfUploader(@Param("checklistId") String checklistId, @Param("uploader") String uploader,
                                   @Param("statuses") Collection<MediaStatus> statuses);

    @Query("select m.checklistId from SurveyMediaEntity m where m.id = :id")
    Optional<String> findChecklistIdById(@Param("id") String id);

    Optional<SurveyMediaEntity> findByChecklistIdAndUploadedByUserIdAndUploadKey(String checklistId,
                                                                                 String uploadedByUserId,
                                                                                 String uploadKey);

    @Query(value = MediaSql.FIND_COMMITTED_HASH_ON_OTHER_VISITS, nativeQuery = true)
    List<SurveyMediaEntity> findCommittedHashOnOtherVisits(@Param("leadId") String leadId,
                                                           @Param("checklistId") String checklistId,
                                                           @Param("sha256") String sha256);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = MediaSql.CLAIM_IF_AWAITING, nativeQuery = true)
    int claimIfAwaiting(@Param("id") String id, @Param("token") String token, @Param("attemptKey") String attemptKey,
                        @Param("now") Instant now, @Param("createdAfter") Instant createdAfter);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = MediaSql.RELEASE_CLAIM_IF_TOKEN, nativeQuery = true)
    int releaseClaimIfToken(@Param("id") String id, @Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = MediaSql.EXPIRE_STALE, nativeQuery = true)
    int expireStale(@Param("checklistId") String checklistId, @Param("cutoff") Instant cutoff,
                    @Param("now") Instant now);
}
