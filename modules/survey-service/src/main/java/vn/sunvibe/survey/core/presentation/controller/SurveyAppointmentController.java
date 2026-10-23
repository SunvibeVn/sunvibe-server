package vn.sunvibe.survey.core.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
import vn.sunvibe.survey.core.application.query.SurveyListQuery;
import vn.sunvibe.survey.core.application.query.SurveyListScope;
import vn.sunvibe.survey.core.application.usecase.SurveyAppointmentCommandUseCase;
import vn.sunvibe.survey.core.application.usecase.SurveyQueryUseCase;
import vn.sunvibe.survey.core.presentation.converter.SurveyRequestMapper;
import vn.sunvibe.survey.core.presentation.converter.SurveyResponseMapper;
import vn.sunvibe.survey.core.presentation.converter.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.request.CancelAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ChangeAppointmentRequest;
import vn.sunvibe.survey.core.presentation.dto.request.ConfirmationRequest;
import vn.sunvibe.survey.core.presentation.dto.request.RecordVisitRequest;
import vn.sunvibe.survey.core.presentation.dto.response.SecurityError;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyCommandResponse;
import vn.sunvibe.survey.core.presentation.dto.response.SurveyPageResponse;

import java.util.Locale;
import java.util.Optional;

/**
 * The survey appointments of staff (survey-schedule spec 7.1): the survey list and the commands on one appointment.
 * Sale staff and ADMIN only; a SALE sees and works only the appointments of the leads assigned to them, and an
 * appointment of a lead they may not see answers 404 exactly like one that does not exist (survey-schedule BR-1).
 */
@RestController
@RequestMapping(SurveyAppointmentController.PATH)
@Tag(name = "Surveys (staff)")
@RequiredArgsConstructor
// survey-schedule BR-1: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyAppointmentController {

    public static final String PATH = "/api/v1/staff/survey-appointments";

    private final SurveyQueryUseCase queryUseCase;
    private final SurveyAppointmentCommandUseCase commandUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final SurveyRequestMapper requestMapper;
    private final SurveyResponseMapper responseMapper;

    /**
     * GET /api/v1/staff/survey-appointments
     * <p>
     * One page of scheduled appointments in the groups OVERDUE, TODAY, UPCOMING (survey-schedule spec A-11); not
     * access-logged.
     */
    @GetMapping
    // An explicit id: the default "list" would renumber the list operations the UI client already uses
    @Operation(operationId = "listSurveyAppointments",
            summary = "List scheduled survey appointments (SALE, SALE_LEADER, SALES_MANAGER, ADMIN, "
            + "SUPER_ADMIN)",
            description = "scope=mine (default): the caller's own appointments; all: every appointment, optionally "
                    + "of one surveyor (not for a SALE). Ordered by group (OVERDUE, TODAY, UPCOMING; Vietnam days), "
                    + "start, id. The items hold no phone, address, coordinates, access note or on-site contact.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "One page of appointments",
                    content = @Content(schema = @Schema(implementation = SurveyPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on scope, surveyorUserId (only with "
                    + "scope all), page or size",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or a SALE asked "
                    + "for scope all",
                    content = @Content(schema = @Schema(implementation = SecurityError.class)))
    })
    public ResponseEntity<SurveyPageResponse> list(
            @Parameter(schema = @Schema(allowableValues = {"mine", "all"}, defaultValue = "mine"))
            @RequestParam(name = "scope", defaultValue = "mine")
            @Pattern(regexp = "mine|all", message = "Scope must be mine or all.") String scope,
            @Parameter(description = "Only with scope=all: the appointments of this surveyor",
                    schema = @Schema(minLength = 1, maxLength = 26))
            @RequestParam(name = "surveyorUserId", required = false)
            @Size(min = 1, max = 26, message = "Surveyor must be 1 to 26 characters.") String surveyorUserId,
            @Parameter(description = "Zero-based page number")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "Page must be 0 or more.") int page,
            @Parameter(description = "Appointments per page, 1 to 100")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "Size must be from 1 to 100.")
            @Max(value = 100, message = "Size must be from 1 to 100.") int size) {
        SurveyListQuery query = new SurveyListQuery(SurveyListScope.valueOf(scope.toUpperCase(Locale.ROOT)),
                Optional.ofNullable(surveyorUserId), page, size);
        return ResponseEntity.ok(responseMapper.toPage(queryUseCase.list(viewerResolver.current(), query)));
    }

    /**
     * POST /api/v1/staff/survey-appointments/{appointmentId}/changes
     * <p>
     * Change the time, place, access note or on-site contact with a reason (survey-schedule US-2 AC-1).
     */
    @PostMapping("/{appointmentId}/changes")
    @Operation(summary = "Change a scheduled survey appointment (SALE: own leads only; SALE_LEADER, SALES_MANAGER, "
            + "ADMIN, SUPER_ADMIN)",
            description = "Every editable value plus a reason; a new revision keeps them and the earlier ones stay. "
                    + "A change of start, duration or place removes the owner's confirmation. A change of time "
                    + "lists overlapping appointments of the surveyor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The survey block, the lead and its History entry",
                    content = @Content(schema = @Schema(implementation = SurveyCommandResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on a body field (a missing key is "
                    + "required), or without field: Nothing changed; MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such appointment, or the caller may not "
                    + "see its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION (field expectedLeadVersion or "
                    + "expectedAppointmentVersion) or INVALID_STATE_TRANSITION (field status): not scheduled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the surveyor can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyCommandResponse> change(@PathVariable("appointmentId") String appointmentId,
                                                        @Valid @RequestBody ChangeAppointmentRequest request) {
        return ResponseEntity.ok(responseMapper.toCommand(commandUseCase.change(viewerResolver.current(),
                requestMapper.toCommand(appointmentId, request))));
    }

    /**
     * POST /api/v1/staff/survey-appointments/{appointmentId}/confirmation
     * <p>
     * Record or remove the owner's confirmation (survey-schedule US-1 AC-5).
     */
    @PostMapping("/{appointmentId}/confirmation")
    @Operation(summary = "Record or remove the owner's confirmation (SALE: own leads only; SALE_LEADER, "
            + "SALES_MANAGER, ADMIN, SUPER_ADMIN)",
            description = "The same value again changes nothing: 200 with historyEntry null and the lead version "
                    + "unchanged.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The survey block, the lead and its History entry",
                    content = @Content(schema = @Schema(implementation = SurveyCommandResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on a body field; MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such appointment, or the caller may not "
                    + "see its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION or INVALID_STATE_TRANSITION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the surveyor can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyCommandResponse> confirmation(@PathVariable("appointmentId") String appointmentId,
                                                              @Valid @RequestBody ConfirmationRequest request) {
        return ResponseEntity.ok(responseMapper.toCommand(commandUseCase.setConfirmation(viewerResolver.current(),
                requestMapper.toCommand(appointmentId, request))));
    }

    /**
     * POST /api/v1/staff/survey-appointments/{appointmentId}/cancellation
     * <p>
     * Cancel with a reason (survey-schedule US-2 AC-2); the lead goes back to CONTACTED unless it has a current visit.
     */
    @PostMapping("/{appointmentId}/cancellation")
    @Operation(summary = "Cancel a scheduled survey appointment (SALE: own leads only; SALE_LEADER, SALES_MANAGER, "
            + "ADMIN, SUPER_ADMIN)",
            description = "The lead stays SURVEY_SCHEDULED when it has a current visit, else goes back to "
                    + "CONTACTED. An overdue appointment can be cancelled; the surveyor need not be eligible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The survey block, the lead and its History entry",
                    content = @Content(schema = @Schema(implementation = SurveyCommandResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on cancelReason or description; "
                    + "MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such appointment, or the caller may not "
                    + "see its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION or INVALID_STATE_TRANSITION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyCommandResponse> cancel(@PathVariable("appointmentId") String appointmentId,
                                                        @Valid @RequestBody CancelAppointmentRequest request) {
        return ResponseEntity.ok(responseMapper.toCommand(commandUseCase.cancel(viewerResolver.current(),
                requestMapper.toCommand(appointmentId, request))));
    }

    /**
     * POST /api/v1/staff/survey-appointments/{appointmentId}/visit
     * <p>
     * Record that someone reached the property (survey-schedule US-3 AC-1, AC-1a).
     */
    @PostMapping("/{appointmentId}/visit")
    @Operation(summary = "Record the visit of a scheduled survey appointment (SALE: own visit only; SALE_LEADER, "
            + "SALES_MANAGER, ADMIN, SUPER_ADMIN also for others)",
            description = "An own record is the current surveyor's visit, who held the appointment at arrivedAt and "
                    + "can take leads now. Every other record is a proxy record of a manager with proxy.reason and "
                    + "proxy.basis. The lead stays SURVEY_SCHEDULED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The survey block, the lead and its History entry",
                    content = @Content(schema = @Schema(implementation = SurveyCommandResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on arrivedAt, note, "
                    + "performedByUserId, proxy or proxy.*; MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or a SALE records "
                    + "a visit for someone else or a proxy record",
                    content = @Content(schema = @Schema(implementation = SecurityError.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such appointment, or the caller may not "
                    + "see its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION or INVALID_STATE_TRANSITION",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "PERFORMER_MISMATCH (field performedByUserId): that "
                    + "person did not hold the appointment then; SURVEYOR_INELIGIBLE (field performedByUserId): an "
                    + "own record by someone who can no longer take leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SurveyCommandResponse> visit(@PathVariable("appointmentId") String appointmentId,
                                                       @Valid @RequestBody RecordVisitRequest request) {
        return ResponseEntity.ok(responseMapper.toCommand(commandUseCase.recordVisit(viewerResolver.current(),
                requestMapper.toCommand(appointmentId, request))));
    }
}
