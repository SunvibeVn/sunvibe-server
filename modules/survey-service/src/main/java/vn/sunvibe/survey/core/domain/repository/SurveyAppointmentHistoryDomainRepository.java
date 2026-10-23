package vn.sunvibe.survey.core.domain.repository;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Domain repository of the revisions and events of appointments. Insert-only (survey BR-5): a row is never updated
 * or deleted.
 */
public interface SurveyAppointmentHistoryDomainRepository {

    void saveRevisions(SurveyAppointmentId appointmentId, List<AppointmentRevision> revisions);

    void saveEvents(SurveyAppointmentId appointmentId, List<AppointmentEvent> events);

    /** The revisions of each appointment, by revision number; one query. */
    Map<SurveyAppointmentId, List<AppointmentRevision>> findRevisions(Collection<SurveyAppointmentId> appointmentIds);

    /** The events of each appointment in the order they happened; one query. */
    Map<SurveyAppointmentId, List<AppointmentEvent>> findEvents(Collection<SurveyAppointmentId> appointmentIds);
}
