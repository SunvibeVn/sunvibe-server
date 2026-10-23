package vn.sunvibe.survey.core.presentation.converter;

import org.springframework.stereotype.Component;
import vn.sunvibe.survey.core.application.command.BookAppointmentCommand;
import vn.sunvibe.survey.core.application.command.CancelAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ChangeAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ConfirmationCommand;
import vn.sunvibe.survey.core.application.command.MeetingPlaceInput;
import vn.sunvibe.survey.core.application.command.ProxyRecord;
import vn.sunvibe.survey.core.application.command.RecordVisitCommand;
import vn.sunvibe.survey.core.application.command.ResumeSurveyCommand;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.presentation.dto.request.BasisRequest;
import vn.sunvibe.survey.core.presentation.dto.request.BookAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.CancelAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ChangeAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ConfirmationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.MeetingLocationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.RecordVisitRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ResumeSurveyRequest;

import java.util.Optional;

/**
 * Converter - SurveyRequestMapper
 * <p>
 * Turns the survey request bodies into commands; the commands check and clean the values (survey-schedule spec 5.5:
 * body first, before any lookup). A basis is named by its request path ({@code basis} or {@code proxy.basis}).
 */
@Component
public class SurveyRequestMapper {

    public BookAppointmentCommand toCommand(String leadId, BookAppointmentRequest request) {
        return new BookAppointmentCommand(leadId, request.expectedLeadVersion(), request.startAt(),
                request.durationMinutes(), place(request.meetingLocation()), request.locationReason(),
                request.accessNote(), request.onSiteContactName(), request.onSiteContactRole(),
                Boolean.TRUE.equals(request.ownerConfirmed()), request.confirmationNote(), request.resurveyReason());
    }

    public ChangeAppointmentCommand toCommand(String appointmentId, ChangeAppointmentRequest request) {
        return new ChangeAppointmentCommand(appointmentId, request.expectedLeadVersion(),
                request.expectedAppointmentVersion(), request.reason(), request.startAt(), request.durationMinutes(),
                place(request.meetingLocation()), orNull(request.accessNote()), orNull(request.onSiteContactName()),
                orNull(request.onSiteContactRole()));
    }

    public ConfirmationCommand toCommand(String appointmentId, ConfirmationRequest request) {
        return new ConfirmationCommand(appointmentId, request.expectedLeadVersion(),
                request.expectedAppointmentVersion(), request.confirmed(), request.note());
    }

    public CancelAppointmentCommand toCommand(String appointmentId, CancelAppointmentRequest request) {
        return new CancelAppointmentCommand(appointmentId, request.expectedLeadVersion(),
                request.expectedAppointmentVersion(), request.cancelReason(), request.description());
    }

    public RecordVisitCommand toCommand(String appointmentId, RecordVisitRequest request) {
        ProxyRecord proxy = request.proxy() == null ? null
                : new ProxyRecord(request.proxy().reason(), basis("proxy.basis", request.proxy().basis()));
        return new RecordVisitCommand(appointmentId, request.expectedLeadVersion(),
                request.expectedAppointmentVersion(), request.arrivedAt(), request.note(),
                request.performedByUserId(), proxy);
    }

    public ResumeSurveyCommand toCommand(String leadId, ResumeSurveyRequest request) {
        return new ResumeSurveyCommand(leadId, request.expectedLeadVersion(), request.reason(),
                basis("basis", request.basis()));
    }

    static MeetingPlaceInput place(MeetingLocationRequest request) {
        return request == null ? null : new MeetingPlaceInput(request.addressText(), request.provinceCode(),
                request.wardCode(), request.latitude(), request.longitude());
    }

    private static Basis basis(String fieldPrefix, BasisRequest request) {
        return request == null ? null : Basis.of(fieldPrefix, request.confirmedOn(), request.text());
    }

    private static String orNull(Optional<String> value) {
        return value == null ? null : value.orElse(null);
    }
}
