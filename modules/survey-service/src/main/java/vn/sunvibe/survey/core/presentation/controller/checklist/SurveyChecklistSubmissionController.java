package vn.sunvibe.survey.core.presentation.controller.checklist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyChecklistReviewCommandUseCase;
import vn.sunvibe.survey.core.presentation.converter.ChecklistRequestConverter;
import vn.sunvibe.survey.core.presentation.converter.ChecklistResponseConverter;
import vn.sunvibe.survey.core.presentation.resolver.SurveyViewerResolver;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistRevisionRequest;
import vn.sunvibe.survey.core.presentation.dto.request.checklist.ChecklistSubmitRequest;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRevisionStartedResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSubmittedResponse;

/**
 * The submission of survey checklist revisions (survey-checklist submission spec 7.1): the holder submits a revision
 * for review, and the assignee starts the next revision of a visit returned for documents. Sale staff and ADMIN only;
 * a checklist of a lead the caller may not see answers 404 exactly like one that does not exist.
 */
@RestController
@RequestMapping(SurveyChecklistController.PATH)
@Tag(name = "Survey checklists (staff)")
@RequiredArgsConstructor
// survey-checklist spec 4.4: sale staff and ADMIN only; SUPER_ADMIN passes hasRole('ADMIN') through the role hierarchy
@PreAuthorize("hasAnyRole('SALE','SALE_LEADER','SALES_MANAGER','ADMIN')")
public class SurveyChecklistSubmissionController {

    private final SurveyChecklistReviewCommandUseCase reviewUseCase;
    private final SurveyViewerResolver viewerResolver;
    private final ChecklistRequestConverter requestConverter;
    private final ChecklistResponseConverter responseConverter;

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/submission
     * <p>
     * The holder freezes the revision and its photos for review (survey-checklist submission spec 4.3, 7.1).
     */
    @PostMapping("/{checklistId}/submission")
    @Operation(operationId = "submitSurveyChecklist",
            summary = "Submit a survey checklist revision for review (the holder, who is the lead's eligible assignee)",
            description = "Freezes the revision and every selected photo with its hash. The lead keeps its status and "
                    + "version. The same caller repeating a submit gets the revision back and nothing is written.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The revision; historyEntry is null on a repeat",
                    content = @Content(schema = @Schema(implementation = ChecklistSubmittedResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on expectedVersion (a missing or "
                    + "unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_READ_ONLY; CHECKLIST_TAKEOVER_REQUIRED; "
                    + "CONCURRENT_MODIFICATION (field expectedVersion); CHECKLIST_SUBMISSION_NOT_AVAILABLE (field "
                    + "submission); CHECKLIST_NOT_READY (one error per failing gate: appointment, readiness.missing, "
                    + "media.pending, media.limit, media.unavailable)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistSubmittedResponse> submit(@PathVariable("checklistId") String checklistId,
                                                             @RequestBody ChecklistSubmitRequest request) {
        return ResponseEntity.ok(responseConverter.toSubmitted(reviewUseCase.submit(viewerResolver.current(),
                requestConverter.toCommand(checklistId, request))));
    }

    /**
     * POST /api/v1/staff/survey-checklists/{checklistId}/revisions
     * <p>
     * The assignee starts the next revision of a visit returned for documents (survey-checklist submission spec 4.6).
     */
    @PostMapping("/{checklistId}/revisions")
    @Operation(operationId = "startSurveyChecklistRevision",
            summary = "Start the next revision of a survey checklist returned for documents (the lead's eligible "
                    + "assignee)",
            description = "A DRAFT on the same visit with the groups as submitted and every usable photo carried. Who "
                    + "did not hold the previous revision declares where the information comes from.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The new revision and its History entry",
                    content = @Content(schema = @Schema(implementation = ChecklistRevisionStartedResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED on intake or intake.* (a missing or "
                    + "unknown key too); MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not sale staff or ADMIN, or not the lead's "
                    + "assignee",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no such checklist, or the caller may not see "
                    + "its lead",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CHECKLIST_NOT_ALLOWED (field status); "
                    + "CHECKLIST_ALREADY_STARTED: a later revision exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "SURVEYOR_INELIGIBLE: the assignee can no longer take "
                    + "leads",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistRevisionStartedResponse> startRevision(
            @PathVariable("checklistId") String checklistId, @RequestBody ChecklistRevisionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(responseConverter.toRevisionStarted(
                reviewUseCase.startRevision(viewerResolver.current(), requestConverter.toCommand(checklistId, request))));
    }
}
