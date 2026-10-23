package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository of the SurveyAppointment aggregate. Appointments are loaded with every revision and event.
 */
public interface SurveyAppointmentDomainRepository {

    /**
     * Insert a new appointment (version null) or update an existing one guarded by its version, then add its pending
     * revisions and events; written at once (flushed). On success the appointment carries the saved version and has
     * nothing pending. A stale version fails with an optimistic locking failure.
     *
     * @throws AppointmentAlreadyScheduledException the lead already has another scheduled appointment
     *                                               (survey-schedule spec I-1)
     */
    void save(SurveyAppointment appointment);

    Optional<SurveyAppointment> findById(SurveyAppointmentId id);

    /** Every appointment of the lead, newest (highest number) first. */
    List<SurveyAppointment> findByLeadId(String leadId);

    /** The scheduled appointment of the lead, if any (there is at most one). */
    Optional<SurveyAppointment> findScheduledByLeadId(String leadId);

    /** The number the next appointment of the lead gets: one more than the highest so far, 1 for the first. */
    int nextAppointmentNo(String leadId);

    /**
     * The scheduled appointments of the surveyor whose time overlaps [start, end), oldest start first; touching
     * intervals do not overlap (survey-schedule US-1 AC-4, spec A-10).
     */
    List<SurveyAppointment> findOverlapping(String surveyorUserId, Instant start, Instant end,
                                            Optional<SurveyAppointmentId> excludeOrEmpty);

    /**
     * One page of the scheduled appointments that match, ordered by list group (OVERDUE, TODAY, UPCOMING;
     * survey-schedule spec A-11, computed from {@code now} and {@code tomorrowStart}), then start, then id. The page is
     * zero-based.
     */
    PageResult<SurveyAppointment> searchScheduled(ScheduledSearch search, Instant now, Instant tomorrowStart,
                                                  int page, int size);
}
