package vn.sunvibe.survey.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStartStateResponse;

import java.util.List;

/**
 * The Survey section of a lead (survey-schedule spec 7.2): appointments and resumptions newest first. Reading it is
 * recorded in the survey access log.
 */
@Schema(description = "The survey block of an owner lead")
public record SurveyBlockResponse(String leadId, int intentCycle, SurveyBookingResponse booking,
                                  String currentVisitId, boolean resumeAllowed,
                                  List<SurveyAppointmentResponse> appointments,
                                  List<SurveyResumptionResponse> resumptions,
                                  @Schema(description = "Whether the viewer can start a survey checklist on the "
                                          + "current visit (survey-checklist spec 7.2)")
                                  ChecklistStartStateResponse checklistStart) {
}
