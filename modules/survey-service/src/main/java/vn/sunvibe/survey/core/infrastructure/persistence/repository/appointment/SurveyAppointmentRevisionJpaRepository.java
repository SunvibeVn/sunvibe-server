package vn.sunvibe.survey.core.infrastructure.persistence.repository.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.appointment.SurveyAppointmentRevisionEntity;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA Repository Interface - used by SurveyAppointmentHistoryRepositoryAdapter.
 */
@Repository
public interface SurveyAppointmentRevisionJpaRepository extends JpaRepository<SurveyAppointmentRevisionEntity, Long> {

    List<SurveyAppointmentRevisionEntity> findByAppointmentIdInOrderByAppointmentIdAscRevisionNoAsc(
            Collection<String> appointmentIds);
}
