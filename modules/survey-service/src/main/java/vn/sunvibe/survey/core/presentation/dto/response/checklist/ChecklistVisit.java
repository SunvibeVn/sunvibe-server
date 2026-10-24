package vn.sunvibe.survey.core.presentation.dto.response.checklist;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyLocationResponse;

import java.time.Instant;

/** The visit a checklist belongs to, as it was recorded (survey-checklist spec 7.2, I-4). */
@Schema(description = "The visit a checklist belongs to, as it was recorded")
public record ChecklistVisit(
        String appointmentId,
        int appointmentNo,
        AppointmentKind kind,
        SurveyIntent intent,
        int intentCycle,
        Instant arrivedAt,
        String performedByUserId,
        @Schema(nullable = true)
        String performedByName,
        VisitRecordKind recordKind,
        String recordedByUserId,
        @Schema(nullable = true)
        String recordedByName,
        Instant recordedAt,
        SurveyLocationResponse meetingLocation,
        SurveyLocationResponse leadLocation) {

    /** Survey data may be private: never print it. */
    @Override
    public String toString() {
        return "ChecklistVisit[" + appointmentId + "]";
    }
}
