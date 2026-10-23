package vn.sunvibe.survey.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEventEntity;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by SurveyAppointmentHistoryRepositoryAdapter.
 */
@Repository
public interface SurveyAppointmentEventJpaRepository extends JpaRepository<SurveyAppointmentEventEntity, Long> {

    // The Snowflake id grows with time, so it keeps the order of events written in the same instant
    List<SurveyAppointmentEventEntity> findByAppointmentIdInOrderByOccurredAtAscIdAsc(
            Collection<String> appointmentIds);
}
