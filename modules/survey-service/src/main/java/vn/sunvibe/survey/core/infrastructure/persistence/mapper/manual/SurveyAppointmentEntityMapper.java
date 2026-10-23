package vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentRevision;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.Cancellation;
import vn.sunvibe.survey.core.domain.model.appointment.Confirmation;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.infrastructure.persistence.entity.SurveyAppointmentEntity;

import java.util.List;

/**
 * Mapper to convert between the SurveyAppointment Domain Model and SurveyAppointmentEntity.
 * Domain → Entity: When saving to database (the row only; revisions and events are written by the history adapter).
 * Entity → Domain: When retrieving from database (rebuilt with SurveyAppointment.load with its history).
 */
@Component
public class SurveyAppointmentEntityMapper {

    /**
     * Convert Entity → Domain.
     */
    public SurveyAppointment toDomain(SurveyAppointmentEntity entity, List<AppointmentRevision> revisions,
                                      List<AppointmentEvent> events) {
        if (entity == null) return null;

        MeetingLocation meeting = new MeetingLocation(entity.getMeetingAddress(), entity.getMeetingProvinceCode(),
                entity.getMeetingProvinceName(), entity.getMeetingWardCode(), entity.getMeetingWardName(),
                Coordinates.ofNullable(entity.getMeetingLatitude(), entity.getMeetingLongitude()));
        AppointmentDetails details = new AppointmentDetails(entity.getStartAt(), entity.getDurationMinutes(), meeting,
                entity.getAccessNote(), entity.getOnSiteContactName(), entity.getOnSiteContactRole());
        LeadLocationSnapshot leadLocation = new LeadLocationSnapshot(entity.getLeadAddressDetail(),
                entity.getLeadProvinceCode(), entity.getLeadProvinceName(), entity.getLeadWardCode(),
                entity.getLeadWardName(), Coordinates.ofNullable(entity.getLeadLatitude(), entity.getLeadLongitude()));
        Confirmation confirmation = new Confirmation(entity.getConfirmationState(), entity.getConfirmationCause(),
                entity.isEverConfirmed(), entity.getConfirmationChangedBy(), entity.getConfirmationChangedAt(),
                entity.getConfirmationNote());
        VisitRecord visit = entity.getVisitRecordKind() == null ? null
                : new VisitRecord(entity.getVisitRecordKind(), entity.getPerformedBy(), entity.getArrivedAt(),
                        entity.getVisitRevisionNo(), entity.getVisitNote(), entity.getVisitRecordedBy(),
                        entity.getVisitRecordedAt(), entity.getProxyReason(),
                        entity.getBasisConfirmedOn() == null ? null
                                : new Basis(entity.getBasisConfirmedOn(), entity.getBasisText()));
        Cancellation cancellation = entity.getCancelReason() == null ? null
                : new Cancellation(entity.getCancelReason(), entity.getCancelDescription(), entity.getCancelledBy(),
                        entity.getCancelledAt());

        return SurveyAppointment.load(
                SurveyAppointmentId.of(entity.getId()),
                entity.getLeadId(),
                entity.getAppointmentNo(),
                entity.getKind(),
                entity.getPreviousVisitId() == null ? null : SurveyAppointmentId.of(entity.getPreviousVisitId()),
                entity.getResurveyReason(),
                entity.getIntent(),
                entity.getIntentCycle(),
                entity.getStatus(),
                details,
                entity.getCurrentRevisionNo(),
                leadLocation,
                entity.getLocationReason(),
                entity.getSurveyorUserId(),
                confirmation,
                visit,
                cancellation,
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion(),
                revisions,
                events
        );
    }

    /**
     * Convert Domain → Entity.
     */
    public SurveyAppointmentEntity toEntity(SurveyAppointment domain) {
        if (domain == null) return null;

        AppointmentDetails details = domain.getDetails();
        MeetingLocation meeting = details.meetingLocation();
        LeadLocationSnapshot lead = domain.getLeadLocation();
        Confirmation confirmation = domain.getConfirmation();
        VisitRecord visit = domain.getVisit();
        Cancellation cancellation = domain.getCancellation();

        return SurveyAppointmentEntity.builder()
                .id(domain.getId().getValue())
                .leadId(domain.getLeadId())
                .appointmentNo(domain.getAppointmentNo())
                .kind(domain.getKind())
                .previousVisitId(domain.getPreviousVisitId() == null ? null : domain.getPreviousVisitId().getValue())
                .resurveyReason(domain.getResurveyReason())
                .intent(domain.getIntent())
                .intentCycle(domain.getIntentCycle())
                .status(domain.getStatus())
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
                .currentRevisionNo(domain.getCurrentRevisionNo())
                .leadAddressDetail(lead.addressDetail())
                .leadProvinceCode(lead.provinceCode())
                .leadProvinceName(lead.provinceName())
                .leadWardCode(lead.wardCode())
                .leadWardName(lead.wardName())
                .leadLatitude(lead.coordinates() == null ? null : lead.coordinates().latitude())
                .leadLongitude(lead.coordinates() == null ? null : lead.coordinates().longitude())
                .locationReason(domain.getLocationReason())
                .surveyorUserId(domain.getSurveyorUserId())
                .confirmationState(confirmation.state())
                .confirmationCause(confirmation.cause())
                .everConfirmed(confirmation.everConfirmed())
                .confirmationChangedBy(confirmation.changedBy())
                .confirmationChangedAt(confirmation.changedAt())
                .confirmationNote(confirmation.note())
                .visitRecordKind(visit == null ? null : visit.kind())
                .performedBy(visit == null ? null : visit.performedBy())
                .arrivedAt(visit == null ? null : visit.arrivedAt())
                .visitRevisionNo(visit == null ? null : visit.revisionNo())
                .visitNote(visit == null ? null : visit.note())
                .visitRecordedBy(visit == null ? null : visit.recordedBy())
                .visitRecordedAt(visit == null ? null : visit.recordedAt())
                .proxyReason(visit == null ? null : visit.proxyReason())
                .basisConfirmedOn(visit == null || visit.basis() == null ? null : visit.basis().confirmedOn())
                .basisText(visit == null || visit.basis() == null ? null : visit.basis().text())
                .cancelReason(cancellation == null ? null : cancellation.reason())
                .cancelDescription(cancellation == null ? null : cancellation.description())
                .cancelledBy(cancellation == null ? null : cancellation.cancelledBy())
                .cancelledAt(cancellation == null ? null : cancellation.cancelledAt())
                .createdBy(domain.getCreatedBy())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .version(domain.getVersion())
                .build();
    }
}
