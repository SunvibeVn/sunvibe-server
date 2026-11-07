package vn.sunvibe.survey.core.presentation.dto.response.appointment;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRevisionStartResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStartStateResponse;

import java.util.List;

/**
 * The Survey section of a lead (survey-schedule spec 7.2): appointments and resumptions newest first. Reading it is
 * recorded in the survey access log.
 */
@Schema(description = "The survey block of an owner lead")
public record SurveyBlockResponse(String leadId, int intentCycle, SurveyBookingResponse booking,
                                  @Schema(nullable = true) String currentVisitId, boolean resumeAllowed,
                                  List<SurveyAppointmentResponse> appointments,
                                  List<SurveyResumptionResponse> resumptions,
                                  @Schema(description = "Whether the viewer can start a survey checklist on the "
                                          + "current visit (survey-checklist spec 7.2)")
                                  ChecklistStartStateResponse checklistStart,
                                  @Schema(description = "Whether the viewer can start a new revision on the current "
                                          + "visit (survey-checklist submission spec 7.2)")
                                  ChecklistRevisionStartResponse revisionStart,
                                  SurveyResumeResponse resume,
                                  SurveyRecordResponse survey) {
}
