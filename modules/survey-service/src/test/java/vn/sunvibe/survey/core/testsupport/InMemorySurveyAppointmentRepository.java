package vn.sunvibe.survey.core.testsupport;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.policy.ListGroup;
import vn.sunvibe.survey.core.domain.repository.PageResult;
import vn.sunvibe.survey.core.domain.repository.ScheduledSearch;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written fake of the appointment repository. Like the adapter it keeps its own copy, refuses a stale version
 * with an optimistic locking failure, refuses a second scheduled appointment of a lead and gives the version back. It
 * writes "save:&lt;status&gt;" to the journal shared with the lead port fake.
 */
public class InMemorySurveyAppointmentRepository implements SurveyAppointmentDomainRepository {

    private final Map<SurveyAppointmentId, SurveyAppointment> stored = new LinkedHashMap<>();
    private final List<String> journal;

    public InMemorySurveyAppointmentRepository(List<String> journal) {
        this.journal = journal;
    }

    public List<SurveyAppointment> all() {
        return stored.values().stream().map(InMemorySurveyAppointmentRepository::copy).toList();
    }

    @Override
    public void save(SurveyAppointment appointment) {
        journal.add("save:" + appointment.getStatus());
        SurveyAppointment existing = stored.get(appointment.getId());
        long newVersion;
        if (existing == null) {
            if (appointment.getVersion() != null) {
                throw new IllegalStateException("A new appointment has no version.");
            }
            newVersion = 0L;
        } else if (!existing.getVersion().equals(appointment.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(SurveyAppointment.class, appointment.getId().getValue());
        } else {
            newVersion = existing.getVersion() + 1;
        }
        if (appointment.getStatus() == AppointmentStatus.SCHEDULED && stored.values().stream()
                .anyMatch(other -> !other.getId().equals(appointment.getId())
                        && other.getLeadId().equals(appointment.getLeadId())
                        && other.getStatus() == AppointmentStatus.SCHEDULED)) {
            throw new AppointmentAlreadyScheduledException();
        }
        appointment.markPersisted(newVersion);
        stored.put(appointment.getId(), copy(appointment));
    }

    @Override
    public Optional<SurveyAppointment> findById(SurveyAppointmentId id) {
        return Optional.ofNullable(stored.get(id)).map(InMemorySurveyAppointmentRepository::copy);
    }

    @Override
    public List<SurveyAppointment> findByLeadId(String leadId) {
        return stored.values().stream()
                .filter(appointment -> appointment.getLeadId().equals(leadId))
                .sorted(Comparator.comparingInt(SurveyAppointment::getAppointmentNo).reversed())
                .map(InMemorySurveyAppointmentRepository::copy)
                .toList();
    }

    @Override
    public Optional<SurveyAppointment> findScheduledByLeadId(String leadId) {
        return findByLeadId(leadId).stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .findFirst();
    }

    @Override
    public int nextAppointmentNo(String leadId) {
        return findByLeadId(leadId).stream().mapToInt(SurveyAppointment::getAppointmentNo).max().orElse(0) + 1;
    }

    @Override
    public List<SurveyAppointment> findOverlapping(String surveyorUserId, Instant start, Instant end,
                                                   Optional<SurveyAppointmentId> excludeOrEmpty) {
        return stored.values().stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .filter(appointment -> appointment.getSurveyorUserId().equals(surveyorUserId))
                .filter(appointment -> excludeOrEmpty.map(id -> !id.equals(appointment.getId())).orElse(true))
                .filter(appointment -> appointment.getDetails().startAt().isBefore(end)
                        && appointment.getDetails().startAt()
                        .plus(Duration.ofMinutes(appointment.getDetails().durationMinutes())).isAfter(start))
                .sorted(Comparator.comparing(appointment -> appointment.getDetails().startAt()))
                .map(InMemorySurveyAppointmentRepository::copy)
                .toList();
    }

    @Override
    public PageResult<SurveyAppointment> searchScheduled(ScheduledSearch search, Instant now, Instant tomorrowStart,
                                                         int page, int size) {
        List<SurveyAppointment> matching = stored.values().stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.SCHEDULED)
                .filter(appointment -> search.surveyorUserId()
                        .map(id -> id.equals(appointment.getSurveyorUserId())).orElse(true))
                .sorted(Comparator.<SurveyAppointment>comparingInt(appointment -> ListGroup.of(
                                appointment.getDetails().startAt(), appointment.getDetails().durationMinutes(), now)
                        .ordinal())
                        .thenComparing(appointment -> appointment.getDetails().startAt())
                        .thenComparing(appointment -> appointment.getId().getValue()))
                .toList();
        List<SurveyAppointment> items = matching.stream().skip((long) page * size).limit(size)
                .map(InMemorySurveyAppointmentRepository::copy).toList();
        return new PageResult<>(items, page, size, matching.size());
    }

    private static SurveyAppointment copy(SurveyAppointment a) {
        return SurveyAppointment.load(a.getId(), a.getLeadId(), a.getAppointmentNo(), a.getKind(),
                a.getPreviousVisitId(), a.getResurveyReason(), a.getIntent(), a.getIntentCycle(), a.getStatus(),
                a.getDetails(), a.getCurrentRevisionNo(), a.getLeadLocation(), a.getLocationReason(),
                a.getSurveyorUserId(), a.getConfirmation(), a.getVisit(), a.getCancellation(), a.getCreatedBy(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getVersion(), a.getRevisions(), a.getEvents());
    }
}
