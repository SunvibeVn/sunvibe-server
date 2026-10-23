package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.repository.PageResult;
import vn.sunvibe.survey.core.domain.repository.ScheduledSearch;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentHistoryDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual.SurveyAppointmentEntityMapper;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyAppointmentJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Repository Implementation - SurveyAppointmentRepositoryAdapter
 * <p>
 * Implements the domain SurveyAppointmentDomainRepository. An appointment with a version is saved as an update guarded
 * by that version (a stale one fails with an optimistic locking error); the row is written and flushed before its new
 * revisions and events. Appointments are always loaded with their history, in one query per table for a whole list.
 */
@Service
public class SurveyAppointmentRepositoryAdapter implements SurveyAppointmentDomainRepository {

    /** The partial unique index of survey-schedule spec I-1 (one scheduled appointment per lead). */
    static final String ONE_SCHEDULED_INDEX = "uk_survey_appointments_one_scheduled";
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final String NO_EXCLUSION = "";

    private final SurveyAppointmentJpaRepository jpaRepository;
    private final SurveyAppointmentHistoryDomainRepository history;
    private final SurveyAppointmentEntityMapper mapper;

    public SurveyAppointmentRepositoryAdapter(SurveyAppointmentJpaRepository jpaRepository,
                                              SurveyAppointmentHistoryDomainRepository history,
                                              SurveyAppointmentEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.history = history;
        this.mapper = mapper;
    }

    @Override
    public void save(SurveyAppointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("SurveyAppointment cannot be null");
        }
        SurveyAppointmentEntity saved;
        try {
            saved = jpaRepository.saveAndFlush(mapper.toEntity(appointment));
        } catch (DataIntegrityViolationException e) {
            if (violates(e, ONE_SCHEDULED_INDEX)) {
                throw new AppointmentAlreadyScheduledException(e);
            }
            throw e;
        }
        history.saveRevisions(appointment.getId(), appointment.getPendingRevisions());
        history.saveEvents(appointment.getId(), appointment.getPendingEvents());
        appointment.markPersisted(saved.getVersion());
    }

    @Override
    public Optional<SurveyAppointment> findById(SurveyAppointmentId id) {
        return jpaRepository.findById(id.getValue()).map(entity -> withHistory(List.of(entity)).get(0));
    }

    @Override
    public List<SurveyAppointment> findByLeadId(String leadId) {
        return withHistory(jpaRepository.findByLeadIdOrderByAppointmentNoDesc(leadId));
    }

    @Override
    public Optional<SurveyAppointment> findScheduledByLeadId(String leadId) {
        return jpaRepository.findByLeadIdAndStatus(leadId, AppointmentStatus.SCHEDULED)
                .map(entity -> withHistory(List.of(entity)).get(0));
    }

    @Override
    public int nextAppointmentNo(String leadId) {
        return jpaRepository.findMaxAppointmentNo(leadId) + 1;
    }

    @Override
    public List<SurveyAppointment> findOverlapping(String surveyorUserId, Instant start, Instant end,
                                                   Optional<SurveyAppointmentId> excludeOrEmpty) {
        String exclude = excludeOrEmpty.map(SurveyAppointmentId::getValue).orElse(NO_EXCLUSION);
        return loadInOrder(jpaRepository.findOverlappingIds(surveyorUserId, start, end, exclude));
    }

    @Override
    public PageResult<SurveyAppointment> searchScheduled(ScheduledSearch search, Instant now, Instant tomorrowStart,
                                                         int page, int size) {
        long offset = (long) page * size;
        List<String> ids;
        long total;
        if (search.surveyorUserId().isPresent()) {
            String surveyor = search.surveyorUserId().get();
            ids = jpaRepository.findScheduledIdsPageOfSurveyor(surveyor, now, tomorrowStart, size, offset);
            total = jpaRepository.countBySurveyorUserIdAndStatus(surveyor, AppointmentStatus.SCHEDULED);
        } else {
            ids = jpaRepository.findScheduledIdsPage(now, tomorrowStart, size, offset);
            total = jpaRepository.countByStatus(AppointmentStatus.SCHEDULED);
        }
        return new PageResult<>(loadInOrder(ids), page, size, total);
    }

    /** The appointments of the ids, with their history, in the order of the ids. */
    private List<SurveyAppointment> loadInOrder(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, SurveyAppointmentEntity> byId = jpaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SurveyAppointmentEntity::getId, Function.identity()));
        return withHistory(ids.stream().map(byId::get).filter(entity -> entity != null).toList());
    }

    private List<SurveyAppointment> withHistory(List<SurveyAppointmentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Collection<SurveyAppointmentId> ids = entities.stream()
                .map(entity -> SurveyAppointmentId.of(entity.getId())).toList();
        Map<SurveyAppointmentId, List<AppointmentRevision>> revisions = history.findRevisions(ids);
        Map<SurveyAppointmentId, List<AppointmentEvent>> events = history.findEvents(ids);
        return entities.stream()
                .map(entity -> {
                    SurveyAppointmentId id = SurveyAppointmentId.of(entity.getId());
                    return mapper.toDomain(entity, revisions.get(id), events.get(id));
                })
                .toList();
    }

    /** True when the database refused the write because of the named constraint or unique index. */
    static boolean violates(Throwable e, String constraintName) {
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
