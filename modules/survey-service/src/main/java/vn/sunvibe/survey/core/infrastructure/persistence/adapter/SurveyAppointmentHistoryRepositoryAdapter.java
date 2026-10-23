package vn.sunvibe.survey.core.infrastructure.persistence.adapter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentHistoryDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEventEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentRevisionEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual.SurveyAppointmentHistoryEntityMapper;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyAppointmentEventJpaRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.repository.SurveyAppointmentRevisionJpaRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository Implementation - SurveyAppointmentHistoryRepositoryAdapter
 * <p>
 * Implements the domain SurveyAppointmentHistoryDomainRepository. Revisions and events are insert-only (survey
 * BR-5), so this adapter offers no way to update or delete one. New rows get Snowflake ids in the order given, which
 * keeps the order of events written in the same instant.
 */
@Service
public class SurveyAppointmentHistoryRepositoryAdapter implements SurveyAppointmentHistoryDomainRepository {

    private final SurveyAppointmentRevisionJpaRepository revisionRepository;
    private final SurveyAppointmentEventJpaRepository eventRepository;
    private final SurveyAppointmentHistoryEntityMapper mapper;
    private final IdGenerator<Long> idGenerator;

    public SurveyAppointmentHistoryRepositoryAdapter(SurveyAppointmentRevisionJpaRepository revisionRepository,
                                                     SurveyAppointmentEventJpaRepository eventRepository,
                                                     SurveyAppointmentHistoryEntityMapper mapper,
                                                     @Qualifier("Snowflake") IdGenerator<Long> idGenerator) {
        this.revisionRepository = revisionRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public void saveRevisions(SurveyAppointmentId appointmentId, List<AppointmentRevision> revisions) {
        if (revisions.isEmpty()) {
            return;
        }
        List<SurveyAppointmentRevisionEntity> rows = new ArrayList<>();
        for (AppointmentRevision revision : revisions) {
            rows.add(mapper.toEntity(idGenerator.generateId(), appointmentId.getValue(), revision));
        }
        revisionRepository.saveAll(rows);
    }

    @Override
    public void saveEvents(SurveyAppointmentId appointmentId, List<AppointmentEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        List<SurveyAppointmentEventEntity> rows = new ArrayList<>();
        for (AppointmentEvent event : events) {
            rows.add(mapper.toEntity(idGenerator.generateId(), appointmentId.getValue(), event));
        }
        eventRepository.saveAll(rows);
    }

    @Override
    public Map<SurveyAppointmentId, List<AppointmentRevision>> findRevisions(
            Collection<SurveyAppointmentId> appointmentIds) {
        Map<SurveyAppointmentId, List<AppointmentRevision>> found = emptyLists(appointmentIds);
        if (appointmentIds.isEmpty()) {
            return found;
        }
        for (SurveyAppointmentRevisionEntity row : revisionRepository
                .findByAppointmentIdInOrderByAppointmentIdAscRevisionNoAsc(values(appointmentIds))) {
            found.get(SurveyAppointmentId.of(row.getAppointmentId())).add(mapper.toDomain(row));
        }
        return found;
    }

    @Override
    public Map<SurveyAppointmentId, List<AppointmentEvent>> findEvents(Collection<SurveyAppointmentId> appointmentIds) {
        Map<SurveyAppointmentId, List<AppointmentEvent>> found = emptyLists(appointmentIds);
        if (appointmentIds.isEmpty()) {
            return found;
        }
        for (SurveyAppointmentEventEntity row : eventRepository
                .findByAppointmentIdInOrderByOccurredAtAscIdAsc(values(appointmentIds))) {
            found.get(SurveyAppointmentId.of(row.getAppointmentId())).add(mapper.toDomain(row));
        }
        return found;
    }

    private static <T> Map<SurveyAppointmentId, List<T>> emptyLists(Collection<SurveyAppointmentId> ids) {
        Map<SurveyAppointmentId, List<T>> lists = new LinkedHashMap<>();
        ids.forEach(id -> lists.put(id, new ArrayList<>()));
        return lists;
    }

    private static List<String> values(Collection<SurveyAppointmentId> ids) {
        return ids.stream().map(SurveyAppointmentId::getValue).toList();
    }
}
