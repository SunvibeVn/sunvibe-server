package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSummaryResponse;

import java.time.Instant;
import java.util.List;

/**
 * One survey appointment (survey-schedule spec 7.2). {@code cannotBePerformed} is false unless the appointment is
 * scheduled and its surveyor can no longer take leads. Names are current full names, null when unknown.
 */
@Schema(description = "A survey appointment of a lead")
public record SurveyAppointmentResponse(String id, String leadId, int appointmentNo, AppointmentKind kind,
                                        @Schema(nullable = true) String previousVisitId,
                                        @Schema(nullable = true) String resurveyReason, SurveyIntent intent,
                                        int intentCycle, boolean inCurrentCycle, AppointmentStatus status,
                                        Instant startAt, int durationMinutes, SurveyLocationResponse meetingLocation,
                                        SurveyLocationResponse leadLocation,
                                        @Schema(nullable = true) String locationReason,
                                        @Schema(nullable = true) String accessNote,
                                        @Schema(nullable = true) String onSiteContactName,
                                        @Schema(nullable = true) String onSiteContactRole, String surveyorUserId,
                                        @Schema(nullable = true) String surveyorName, boolean cannotBePerformed,
                                        List<SurveyHolderResponse> holders, SurveyConfirmationResponse confirmation,
                                        @Schema(nullable = true) SurveyVisitResponse visit,
                                        @Schema(nullable = true) SurveyCancellationResponse cancellation,
                                        List<SurveyRevisionResponse> revisions, List<SurveyEventResponse> events,
                                        String createdByUserId, @Schema(nullable = true) String createdByName,
                                        Instant createdAt, long version,
                                        @Schema(description = "The survey checklists of a VISITED appointment, "
                                                + "newest first; empty otherwise (survey-checklist spec 7.2)")
                                        List<ChecklistSummaryResponse> checklists) {

    @Override
    public String toString() {
        return "SurveyAppointmentResponse[" + id + "]";
    }
}
