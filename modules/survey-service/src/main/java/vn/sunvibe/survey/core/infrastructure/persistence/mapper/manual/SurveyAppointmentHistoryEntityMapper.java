package vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEventEntity;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentRevisionEntity;

/**
 * Mapper between the revisions and events of an appointment and their entities. The Snowflake id of a new row is
 * given by the caller.
 */
@Component
public class SurveyAppointmentHistoryEntityMapper {

    public AppointmentRevision toDomain(SurveyAppointmentRevisionEntity entity) {
        MeetingLocation meeting = new MeetingLocation(entity.getMeetingAddress(), entity.getMeetingProvinceCode(),
                entity.getMeetingProvinceName(), entity.getMeetingWardCode(), entity.getMeetingWardName(),
                Coordinates.ofNullable(entity.getMeetingLatitude(), entity.getMeetingLongitude()));
        AppointmentDetails details = new AppointmentDetails(entity.getStartAt(), entity.getDurationMinutes(), meeting,
                entity.getAccessNote(), entity.getOnSiteContactName(), entity.getOnSiteContactRole());
        return new AppointmentRevision(entity.getRevisionNo(), entity.getValidFrom(), details, entity.getReason(),
                entity.getActorUserId());
    }

    public SurveyAppointmentRevisionEntity toEntity(long id, String appointmentId, AppointmentRevision revision) {
        AppointmentDetails details = revision.details();
        MeetingLocation meeting = details.meetingLocation();
        return SurveyAppointmentRevisionEntity.builder()
                .id(id)
                .appointmentId(appointmentId)
                .revisionNo(revision.revisionNo())
                .validFrom(revision.validFrom())
                .startAt(details.startAt())
                .durationMinutes(details.durationMinutes())
                .meetingAddress(meeting.addressText())
                .meetingProvinceCode(meeting.provinceCode())
                .meetingProvinceName(meeting.provinceName())
                .meetingWardCode(meeting.wardCode())
                .meetingWardName(meeting.wardName())
                .meetingLatitude(meeting.coordinates() == null ? null : meeting.coordinates().latitude())
                .meetingLongitude(meeting.coordinates() == null ? null : meeting.coordinates().longitude())
                .accessNote(details.accessNote())
                .onSiteContactName(details.onSiteContactName())
                .onSiteContactRole(details.onSiteContactRole())
                .reason(revision.reason())
                .actorUserId(revision.actorUserId())
                .build();
    }

    public AppointmentEvent toDomain(SurveyAppointmentEventEntity entity) {
        return new AppointmentEvent(entity.getType(), entity.getActorUserId(), entity.getOccurredAt(),
                entity.getRevisionNo(), entity.getFromSurveyorId(), entity.getToSurveyorId(),
                entity.getConfirmationCause(), entity.getNote());
    }

    public SurveyAppointmentEventEntity toEntity(long id, String appointmentId, AppointmentEvent event) {
        return SurveyAppointmentEventEntity.builder()
                .id(id)
                .appointmentId(appointmentId)
                .type(event.type())
                .actorUserId(event.actorUserId())
                .occurredAt(event.occurredAt())
                .revisionNo(event.revisionNo())
                .fromSurveyorId(event.fromSurveyorUserId())
                .toSurveyorId(event.toSurveyorUserId())
                .confirmationCause(event.confirmationCause())
                .note(event.note())
                .build();
    }
}
