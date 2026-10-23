package vn.sunvibe.survey.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.survey.core.application.usecase.SurveyAppointmentCommandUseCase;
import vn.sunvibe.survey.core.application.usecase.SurveyQueryUseCase;
import vn.sunvibe.survey.core.presentation.converter.SurveyRequestMapper;
import vn.sunvibe.survey.core.presentation.converter.SurveyResponseMapper;
import vn.sunvibe.survey.core.presentation.converter.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.request.BookAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ResumeSurveyRequest;
import vn.sunvibe.survey.core.presentation.dto.response.SecurityError;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyBlockResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyCommandResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyOverlapsResponse;

import java.time.Instant;
import java.util.Optional;

/**
 * The survey of one owner lead (survey-schedule spec 7.1): its survey block, a booking and the overlap pre-check.
 * Sale staff and ADMIN only; a SALE works only the leads assigned to them, and a lead they may not see answers 404
 * exactly like a lead that does not exist (survey-schedule BR-1).
 */
@RestController
@RequestMapping(LeadSurveyController.PATH)
@Tag(name = "Surveys (staff)")
@RequiredArgsConstructor
// survey-schedule BR-1: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class LeadSurveyController {

    public static final String PATH = "/api/v1/staff/owner-leads";

    private final SurveyQueryUseCase queryUseCase;
    private final SurveyAppointmentCommandUseCase commandUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final SurveyRequestMapper requestMapper;
    private final SurveyResponseMapper responseMapper;

    /**
     * GET /api/v1/staff/owner-leads/{leadId}/survey
     * <p>
     * The Survey section of the lead; every read is recorded in the survey access log (survey-schedule spec A-13).
     */
    @GetMapping("/{leadId}/survey")
    @Operation(summary = "Read the survey block of an owner lead (SALE: own leads only; SALE_LEADER, SALES_MANAGER, "
            + "ADMIN, SUPER_ADMIN)",
            description = "Appointments with places, access notes and on-site contacts, revisions, events, the visit "
                    + "and the resumptions. Every call is recorded in the survey access log.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The survey block",
                    content = @Content(schema = @Schema(implementation = SurveyBlockResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyBlockResponse> block(@PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(responseMapper.toBlock(queryUseCase.block(viewerResolver.current(), leadId)));
    }

    /**
     * POST /api/v1/staff/owner-leads/{leadId}/survey-appointments
     * <p>
     * Book a survey (or a re-survey) already agreed with the owner (survey-schedule US-1, US-3 AC-2).
     */
    @PostMapping("/{leadId}/survey-appointments")
    @Operation(summary = "Book a survey appointment (SALE: own leads only; SALE_LEADER, SALES_MANAGER, ADMIN, "
            + "SUPER_ADMIN)",
            description = "The surveyor is the lead's assignee. A first survey is booked from ASSIGNED or CONTACTED, "
                    + "a re-survey (the current requirement cycle already has a visit; needs resurveyReason) from "
                    + "CONTACTED or SURVEY_SCHEDULED; only rental leads. A meeting place other than the lead's needs "
                    + "locationReason. Overlapping appointments of the surveyor are listed, never refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The survey block, the lead and its History entry",
                    content = @Content(schema = @Schema(implementation = SurveyCommandResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on a body field (startAt, "
                    + "durationMinutes, meetingLocation.*, locationReason, resurveyReason, accessNote, "
                    + "onSiteContactName, onSiteContactRole, confirmationNote, expectedLeadVersion); "
                    + "MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION (field expectedLeadVersion), "
                    + "SURVEY_ALREADY_SCHEDULED, SURVEY_NOT_ALLOWED (field status or intent, with the allowed "
                    + "values) or INVALID_STATE_TRANSITION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyCommandResponse> book(@PathVariable("leadId") String leadId,
                                                      @Valid @RequestBody BookAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toCommand(
                commandUseCase.book(viewerResolver.current(), requestMapper.toCommand(leadId, request))));
    }

    /**
     * GET /api/v1/staff/owner-leads/{leadId}/survey-overlaps
     * <p>
     * The scheduled appointments of the lead's surveyor that overlap the planned time (survey-schedule spec A-10); not
     * access-logged.
     */
    @GetMapping("/{leadId}/survey-overlaps")
    @Operation(summary = "Check overlaps before booking or changing a survey (SALE: own leads only; SALE_LEADER, "
            + "SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "Only request codes and times. On an edit, excludeAppointmentId leaves the appointment "
                    + "itself out. Not recorded in the access log.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The overlapping appointments",
                    content = @Content(schema = @Schema(implementation = SurveyOverlapsResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on startAt, durationMinutes or "
                    + "excludeAppointmentId",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyOverlapsResponse> overlaps(
            @PathVariable("leadId") String leadId,
            @Parameter(description = "Planned start, a whole minute", required = true)
            @RequestParam(name = "startAt") Instant startAt,
            @Parameter(description = "Planned duration, 15..480 in steps of 15", required = true)
            @RequestParam(name = "durationMinutes") Integer durationMinutes,
            @Parameter(description = "On an edit: the appointment itself")
            @RequestParam(name = "excludeAppointmentId", required = false) String excludeAppointmentId) {
        return ResponseEntity.ok(responseMapper.toOverlaps(queryUseCase.overlaps(viewerResolver.current(), leadId,
                startAt, durationMinutes, Optional.ofNullable(excludeAppointmentId))));
    }

    /**
     * POST /api/v1/staff/owner-leads/{leadId}/survey-resumptions
     * <p>
     * Resume the survey work of a held lead from its current visit (survey-schedule BR-11).
     */
    @PostMapping("/{leadId}/survey-resumptions")
    @Operation(summary = "Resume the survey work of a held owner lead (SALE: own leads only; SALE_LEADER, "
            + "SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "ON_HOLD with a current visit goes back to SURVEY_SCHEDULED, with a reason and the owner's "
                    + "confirmation (basis) dated from the day the lead went on hold.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The survey block, the lead and its History entry",
                    content = @Content(schema = @Schema(implementation = SurveyCommandResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on reason or basis.*; "
                    + "MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such lead, or the caller may not see it",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION (field expectedLeadVersion), "
                    + "INVALID_STATE_TRANSITION (field status, allowed ON_HOLD) or NO_CURRENT_VISIT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyCommandResponse> resume(@PathVariable("leadId") String leadId,
                                                        @Valid @RequestBody ResumeSurveyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseMapper.toCommand(
                commandUseCase.resume(viewerResolver.current(), requestMapper.toCommand(leadId, request))));
    }
}
