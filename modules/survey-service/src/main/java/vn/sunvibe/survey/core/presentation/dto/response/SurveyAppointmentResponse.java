package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;

import java.time.Instant;
import java.util.List;

/**
 * One survey appointment (survey-schedule spec 7.2). {@code cannotBePerformed} is false unless the appointment is
 * scheduled and its surveyor can no longer take leads. Names are current full names, null when unknown.
 */
@Schema(description = "A survey appointment of a lead")
public record SurveyAppointmentResponse(String id, String leadId, int appointmentNo, AppointmentKind kind,
                                        String previousVisitId, String resurveyReason, SurveyIntent intent,
                                        int intentCycle, boolean inCurrentCycle, AppointmentStatus status,
                                        Instant startAt, int durationMinutes, SurveyLocationResponse meetingLocation,
                                        SurveyLocationResponse leadLocation, String locationReason, String accessNote,
                                        String onSiteContactName, String onSiteContactRole, String surveyorUserId,
                                        String surveyorName, boolean cannotBePerformed,
                                        List<SurveyHolderResponse> holders, SurveyConfirmationResponse confirmation,
                                        SurveyVisitResponse visit, SurveyCancellationResponse cancellation,
                                        List<SurveyRevisionResponse> revisions, List<SurveyEventResponse> events,
                                        String createdByUserId, String createdByName, Instant createdAt,
                                        long version) {

    @Override
    public String toString() {
        return "SurveyAppointmentResponse[" + id + "]";
    }
}
