package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository Interface - used by SurveyAppointmentRepositoryAdapter
 * (implementation of SurveyAppointmentDomainRepository). The native queries compute appointment ends with PostgreSQL
 * interval arithmetic; their list order is the group rank of survey-schedule spec A-11, then start, then id.
 */
@Repository
public interface SurveyAppointmentJpaRepository extends JpaRepository<SurveyAppointmentEntity, String> {

    /** The lead of an appointment without loading it (survey-checklist spec 5.5). */
    @Query("select a.leadId from SurveyAppointmentEntity a where a.id = :id")
    Optional<String> findLeadIdById(@Param("id") String id);

    String GROUP_RANK = "CASE WHEN a.start_at + make_interval(mins => a.duration_minutes) <= :now THEN 0"
            + " WHEN a.start_at < :tomorrowStart THEN 1 ELSE 2 END";

    List<SurveyAppointmentEntity> findByLeadIdOrderByAppointmentNoDesc(String leadId);

    Optional<SurveyAppointmentEntity> findByLeadIdAndStatus(String leadId, AppointmentStatus status);

    @Query("select coalesce(max(a.appointmentNo), 0) from SurveyAppointmentEntity a where a.leadId = :leadId")
    int findMaxAppointmentNo(@Param("leadId") String leadId);

    @Query(value = "SELECT a.id FROM survey_appointments a WHERE a.status = 'SCHEDULED'"
            + " AND a.surveyor_user_id = :surveyor AND a.id <> :exclude AND a.start_at < :end"
            + " AND a.start_at + make_interval(mins => a.duration_minutes) > :start"
            + " ORDER BY a.start_at, a.id", nativeQuery = true)
    List<String> findOverlappingIds(@Param("surveyor") String surveyorUserId, @Param("start") Instant start,
                                    @Param("end") Instant end, @Param("exclude") String excludeId);

    @Query(value = "SELECT a.id FROM survey_appointments a WHERE a.status = 'SCHEDULED'"
            + " ORDER BY " + GROUP_RANK + ", a.start_at, a.id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<String> findScheduledIdsPage(@Param("now") Instant now, @Param("tomorrowStart") Instant tomorrowStart,
                                      @Param("limit") int limit, @Param("offset") long offset);

    @Query(value = "SELECT a.id FROM survey_appointments a WHERE a.status = 'SCHEDULED'"
            + " AND a.surveyor_user_id = :surveyor"
            + " ORDER BY " + GROUP_RANK + ", a.start_at, a.id LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<String> findScheduledIdsPageOfSurveyor(@Param("surveyor") String surveyorUserId,
                                                @Param("now") Instant now,
                                                @Param("tomorrowStart") Instant tomorrowStart,
                                                @Param("limit") int limit, @Param("offset") long offset);

    long countByStatus(AppointmentStatus status);

    long countBySurveyorUserIdAndStatus(String surveyorUserId, AppointmentStatus status);
}
